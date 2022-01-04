package be.cytomine.service.ontology;

import be.cytomine.domain.CytomineDomain;
import be.cytomine.domain.command.*;
import be.cytomine.domain.image.ImageInstance;
import be.cytomine.domain.image.SliceInstance;
import be.cytomine.domain.ontology.AnnotationDomain;
import be.cytomine.domain.ontology.AnnotationTerm;
import be.cytomine.domain.ontology.Term;
import be.cytomine.domain.ontology.ReviewedAnnotation;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.SecUser;
import be.cytomine.domain.security.User;
import be.cytomine.dto.AnnotationLight;
import be.cytomine.dto.ReviewedAnnotationStatsEntry;
import be.cytomine.dto.SimplifiedAnnotation;
import be.cytomine.dto.UserTermMapping;
import be.cytomine.exceptions.AlreadyExistException;
import be.cytomine.exceptions.CytomineMethodNotYetImplementedException;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.exceptions.WrongArgumentException;
import be.cytomine.repository.ReviewedAnnotationListing;
import be.cytomine.repository.ontology.ReviewedAnnotationRepository;
import be.cytomine.repository.ontology.TermRepository;
import be.cytomine.repository.ontology.UserAnnotationRepository;
import be.cytomine.repository.security.UserRepository;
import be.cytomine.service.AnnotationListingService;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.ModelService;
import be.cytomine.service.command.TransactionService;
import be.cytomine.service.dto.BoundariesCropParameter;
import be.cytomine.service.image.ImageInstanceService;
import be.cytomine.service.image.SliceInstanceService;
import be.cytomine.service.security.SecurityACLService;
import be.cytomine.service.utils.SimplifyGeometryService;
import be.cytomine.service.utils.TaskService;
import be.cytomine.service.utils.ValidateGeometryService;
import be.cytomine.utils.CommandResponse;
import be.cytomine.utils.GeometryUtils;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.Task;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.logging.log4j.ThreadContext.isEmpty;
import static org.springframework.security.acls.domain.BasePermission.DELETE;
import static org.springframework.security.acls.domain.BasePermission.READ;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReviewedAnnotationService extends ModelService {

    private final ReviewedAnnotationRepository reviewedAnnotationRepository;

    private final SecurityACLService securityACLService;

    private final CurrentUserService currentUserService;

    private final AnnotationListingService annotationListingService;

    private final EntityManager entityManager;

    private final SliceInstanceService sliceInstanceService;

    private final ImageInstanceService imageInstanceService;

    private final SimplifyGeometryService simplifyGeometryService;

    private final TransactionService transactionService;

    private final ValidateGeometryService validateGeometryService;

    private final AnnotationTermService annotationTermService;

    private final TermRepository termRepository;

    private final TaskService taskService;

    private final UserAnnotationRepository userAnnotationRepository;

    @Override
    public Class currentDomain() {
        return ReviewedAnnotation.class;
    }

    @Override
    public CytomineDomain createFromJSON(JsonObject json) {
        return new ReviewedAnnotation().buildDomainFromJson(json, entityManager);
    }

    public ReviewedAnnotation get(Long id) {
        return find(id).orElse(null);
    }

    public Optional<ReviewedAnnotation> find(Long id) {
        Optional<ReviewedAnnotation> optionalReviewedAnnotation = reviewedAnnotationRepository.findById(id);
        optionalReviewedAnnotation.ifPresent(reviewedAnnotation -> securityACLService.check(reviewedAnnotation.container(),READ));
        return optionalReviewedAnnotation;
    }

    public Long count(User user) {
        return reviewedAnnotationRepository.countByUser(user);
    }

    public Long countByProject(Project project, Date startDate, Date endDate) {
        if (startDate!=null && endDate!=null) {
            return reviewedAnnotationRepository.countByProjectAndCreatedBetween(project, startDate, endDate);
        } else if (startDate!=null) {
            return reviewedAnnotationRepository.countByProjectAndCreatedAfter(project, startDate);
        } else if (endDate!=null) {
            return reviewedAnnotationRepository.countByProjectAndCreatedBefore(project, endDate);
        } else {
            return reviewedAnnotationRepository.countByProject(project);
        }
    }


    public List list(Project project, List<String> propertiesToShow) {
        securityACLService.check(project.container(), READ);
        ReviewedAnnotationListing reviewedAnnotationListing = new ReviewedAnnotationListing(entityManager);
        reviewedAnnotationListing.setColumnsToPrint(propertiesToShow);
        reviewedAnnotationListing.setProject(project.getId());
        return annotationListingService.executeRequest(reviewedAnnotationListing);
    }

    public List<ReviewedAnnotationStatsEntry> statsGroupByUser(ImageInstance ImageInstance) {
        return reviewedAnnotationRepository.stats(ImageInstance);
    }


    public List listIncluded(ImageInstance image, String geometry, List<Long> terms, AnnotationDomain annotation, List<String> propertiesToShow) {
        securityACLService.check(image.container(), READ);

        ReviewedAnnotationListing reviewedAnnotationListing = new ReviewedAnnotationListing(entityManager);
        reviewedAnnotationListing.setColumnsToPrint(propertiesToShow);
        reviewedAnnotationListing.setImage(image.getId());
        reviewedAnnotationListing.setTerms(terms);
        reviewedAnnotationListing.setExcludedAnnotation((annotation!=null? annotation.getId() : null));
        reviewedAnnotationListing.setBbox(geometry);
        return annotationListingService.executeRequest(reviewedAnnotationListing);
    }


    // TODO: seems useless, no migration?
//    /**
//     * List validate annotation
//     * @param image Image filter
//     * @param bbox Boundary area filter
//     * @return Reviewed Annotation list
//     */
//    def list(ImageInstance image, String bbox, def propertiesToShow = null) {
//        Geometry boundingbox = GeometryUtils.createBoundingBox(bbox)
//        list(image, boundingbox,propertiesToShow)
//    }
//
//    /**
//     * List validate annotation
//     * @param image Image filter
//     * @param bbox Boundary area filter
//     * @return Reviewed Annotation list
//     */
//    def list(ImageInstance image, Geometry bbox, def propertiesToShow = null) {
//        securityACLService.check(image.container(),READ)
//
//
//        def rule = kmeansGeometryService.mustBeReduce(image,null,bbox)
//        if(rule==kmeansGeometryService.FULL) {
//            /**
//             * We will sort annotation so that big annotation that covers a lot of annotation comes first (appear behind little annotation so we can select annotation behind other)
//             * We compute in 'gc' the set of all other annotation that must be list
//             * For each review annotation, we compute the number of other annotation that cover it (ST_CoveredBy => t or f => 0 or 1)
//             *
//             * ST_CoveredBy will return false if the annotation is not perfectly "under" the compare annotation (if some points are outside)
//             * So in gc, we increase the size of each compare annotation just for the check
//             * So if an annotation x is under y but x has some point next outside y, x will appear top (if no resize, it will appear top or behind).
//             */
//            def xfactor = "1.28"
//            def yfactor = "1.28"
//            //TODO:: get zoom info from UI client, display with scaling only with hight zoom (< annotations)
//
//            double imageWidth = image.baseImage.width
//            double bboxWidth = bbox.getEnvelopeInternal().width
//            double ratio = bboxWidth/imageWidth*100
//
//            boolean zoomToLow = ratio > 50
//
//            log.info "imageWidth=$imageWidth"
//            log.info "bboxWidth=$bboxWidth"
//            log.info "ratio=$ratio"
//            log.info  "zoomToLow="+zoomToLow
//            String subRequest
//            if (zoomToLow) {
//                subRequest = "(SELECT SUM(ST_CoveredBy(ga.location,gb.location )::integer) "
//            } else {
//                //too heavy to use with little zoom
//                subRequest = "(SELECT SUM(ST_CoveredBy(ga.location,ST_Translate(ST_Scale(gb.location, $xfactor, $yfactor), ST_X(ST_Centroid(gb.location))*(1 - $xfactor), ST_Y(ST_Centroid(gb.location))*(1 - $yfactor) ))::integer) "
//
//            }
//
//            subRequest =  subRequest +
//                    "FROM reviewed_annotation ga, reviewed_annotation gb " +
//                    "WHERE ga.id=a.id " +
//                    "AND ga.id<>gb.id " +
//                    "AND ga.image_id=gb.image_id " +
//                    "AND ST_Intersects(gb.location,ST_GeometryFromText('" + bbox.toString() + "',0)))\n"
//
//            ReviewedAnnotationListing al = new ReviewedAnnotationListing(
//                    columnToPrint: propertiesToShow,
//                    image: image.id,
//                    bbox : bbox,
//                    orderBy: ['id':'asc']
//            //orderBy: ['numberOfCoveringAnnotation':'asc','id':'asc']
//                )
//
//            //DISABLE COVER BY FOR PERF
//            //al.addExtraColumn("numberOfCoveringAnnotation",subRequest)
//            annotationListingService.executeRequest(al)
//
//        } else {
//
//            ReviewedAnnotationListing al = new ReviewedAnnotationListing(
//                    columnToPrint: propertiesToShow,
//                    kmeans: true,
//                    image: image.id,
//                    avoidEmptyCentroid : true,
//                    bbox : bbox
//                )
//            if(rule==kmeansGeometryService.KMEANSFULL){
//
//                kmeansGeometryService.doKeamsFullRequest(al.getAnnotationsRequest())
//            } else {
//                kmeansGeometryService.doKeamsSoftRequest(al.getAnnotationsRequest())
//            }
//        }
//    }


    public List<UserTermMapping> listTerms(ReviewedAnnotation annotation) {
        Long reviewer = (annotation.getImage().getReviewUser()!=null?
                annotation.getImage().getReviewUser().getId() : null);
        return annotation.getTerms().stream().map(x -> new UserTermMapping(x.getId(), reviewer))
                .collect(Collectors.toList());
    }


    /**
     * Add the new domain with JSON data
     * @param jsonObject New domain data
     * @return Response structure (created domain data,..)
     */
    @Override
    public CommandResponse add(JsonObject jsonObject) {
        securityACLService.check(jsonObject.getJSONAttrLong("project"), Project.class, READ);
        securityACLService.checkIsNotReadOnly(jsonObject.getJSONAttrLong("project"), Project.class);

        SecUser currentUser = currentUserService.getCurrentUser();
        //Start transaction
        Transaction transaction = transactionService.start();

        if (jsonObject.get("location") instanceof Geometry) {
            jsonObject.put("location", validateGeometryService.tryToMakeItValidIfNotValid((Geometry)jsonObject.get("location")));
        } else {
            jsonObject.put("location", validateGeometryService.tryToMakeItValidIfNotValid(jsonObject.getJSONAttrStr("location")));
        }

        synchronized (this.getClass()) {
            CommandResponse commandResponse = executeCommand(new AddCommand(currentUser, transaction), null, jsonObject);
            return commandResponse;
        }
    }


    /**
     * Update this domain with new data from json
     * @param domain Domain to update
     * @param jsonNewData New domain datas
     * @return Response structure (new domain data, old domain data..)
     */
    public CommandResponse update(CytomineDomain domain, JsonObject jsonNewData, Transaction transaction) {
        SecUser currentUser = currentUserService.getCurrentUser();
        securityACLService.checkIsCreator(domain, currentUser);
        CommandResponse result = executeCommand(new EditCommand(currentUser, null), domain, jsonNewData);
        return result;
    }

    /**
     * Delete this domain
     * @param domain Domain to delete
     * @param transaction Transaction link with this command
     * @param task Task for this command
     * @param printMessage Flag if client will print or not confirm message
     * @return Response structure (code, old domain,..)
     */
    @Override
    public CommandResponse delete(CytomineDomain domain, Transaction transaction, Task task, boolean printMessage) {
        SecUser currentUser = currentUserService.getCurrentUser();
        securityACLService.checkIsCreator(domain, currentUser);
        Command c = new DeleteCommand(currentUser, transaction);
        return executeCommand(c,domain, null);
    }



    public CommandResponse reviewAnnotation(Long idAnnotation, List<Long> terms) {

        AnnotationDomain basedAnnotation = AnnotationDomain.getAnnotationDomain(entityManager, idAnnotation);
        if(!basedAnnotation.getImage().isInReviewMode()) {
            throw new WrongArgumentException("Cannot accept annotation, enable image review mode!");
        }
        if(basedAnnotation.getImage().getReviewUser()!=null
                && !Objects.equals(basedAnnotation.getImage().getReviewUser().getId(), currentUserService.getCurrentUser().getId())) {
            throw new WrongArgumentException("You must be the image reviewer to accept annotation. Image reviewer is " + basedAnnotation.getImage().getReviewUser());
        }

        reviewedAnnotationRepository.findByParentIdent(basedAnnotation.getId()).ifPresent(
                duplicate -> {throw new AlreadyExistException("Annotation is already accepted!");}
        );

        ReviewedAnnotation review = createReviewAnnotation(basedAnnotation, terms);
        return this.add(review.toJsonObject());
    }

    public CommandResponse unReviewAnnotation(Long idAnnotation) {
        ReviewedAnnotation reviewedAnnotation = reviewedAnnotationRepository.findByParentIdent(idAnnotation)
                .orElseThrow(() -> new WrongArgumentException("This annotation is not accepted, you cannot reject it!"));

        if(reviewedAnnotation.getImage().getReviewUser()!=null && !Objects.equals(reviewedAnnotation.getImage().getReviewUser().getId(), currentUserService.getCurrentUser().getId())) {
            throw new WrongArgumentException("You must be the image reviewer to reject annotation. Image reviewer is  " + reviewedAnnotation.getImage().getReviewUser().getUsername());
        }
        return this.delete(this.retrieve(JsonObject.of("id", reviewedAnnotation.getId())), null, null, false);
    }


    /**
     * Review annotation with the specified terms
     * @param annotation Annotation to review
     * @param terms Terms to add to the annotation
     * @return The reviewed annotation
     */
    private ReviewedAnnotation createReviewAnnotation(AnnotationDomain annotation, List<Long> terms) {
        ReviewedAnnotation review = new ReviewedAnnotation();
        review.setParentIdent(annotation.getId());
        review.setParentClassName(annotation.getClass().getName());
        review.setStatus(1);
        review.setUser(annotation.user());
        review.setLocation(annotation.getLocation());
        review.setImage(annotation.getImage());
        review.setSlice(annotation.getSlice());
        review.setProject(annotation.getProject());
        review.setGeometryCompression(annotation.getGeometryCompression());

        if(terms!=null) {
            //terms in request param
            for (Long term : terms) {
                review.getTerms().add(termRepository.findById(term)
                        .orElseThrow(() -> new ObjectNotFoundException("Term", term)));
            }
        } else {
            //nothing in param, add term from annotation
            for (Term term : annotation.termsForReview()) {
                review.getTerms().add(term);
            }

        }
        review.setReviewUser(currentUserService.getCurrentUser());
        return review;
    }


    private final UserRepository userRepository;

    public List<Long> reviewLayer(Long imageInstanceId, List<Long> usersIds, Task task) {

        taskService.updateTask(task, 2, "Extract parameters...");
        if (usersIds==null || usersIds.isEmpty()) {
            throw new WrongArgumentException("There is no layer:" + usersIds);
        }
        List<SecUser> users = usersIds.stream()
                .map(x -> userRepository.findById(x).orElseThrow(() -> new ObjectNotFoundException("User", x))).collect(Collectors.toList());
        ImageInstance imageInstance = imageInstanceService.find(imageInstanceId)
                .orElseThrow(() -> new ObjectNotFoundException("ImageInstance", imageInstanceId));

        if (!imageInstance.isInReviewMode()) {
            throw new WrongArgumentException("Cannot review annotation, enable image review mode!");
        } else if (imageInstance.getReviewUser() != null && !Objects.equals(imageInstance.getReviewUser().getId(), currentUserService.getCurrentUser().getId())) {
            throw new WrongArgumentException("You must be the image reviewer to review annotation. Image reviewer is " + imageInstance.getReviewUser());
        } else if (users.isEmpty()) {
            throw new WrongArgumentException("There is no layer:" + usersIds);
        }

        taskService.updateTask(task, 2, "Extract parameters...");

        List<AnnotationDomain> annotations = new ArrayList<>();

        //get all annotations for each user
        taskService.updateTask(task,5,"Look for all annotations...");

        for (SecUser user : users) {
            if (user.isAlgo()) {
                throw new CytomineMethodNotYetImplementedException(""); // TODO
            } else {
                annotations.addAll(userAnnotationRepository.findAllByUserAndImage((User)user, imageInstance));
            }
        }

        //review each annotation
        taskService.updateTask(task,10,annotations.size() + " annotations found...");

        int realReviewed = 0;
        int taskRefresh =  annotations.size()>1000? 100 : 10;
        List<Long> reviewedIds = new ArrayList<>();
        for(int i = 0; i < annotations.size(); i++) {
            if (i%taskRefresh==0) {
                taskService.updateTask(task,10+(int)(((double)i/(double)annotations.size())*0.9d*100),realReviewed + " new reviewed annotations...");
            }
            entityManager.refresh(annotations.get(i));
            if (reviewedAnnotationRepository.findByParentIdent(annotations.get(i).getId()).isEmpty()) {
                //if not yet reviewed, review it
                realReviewed++;
                CommandResponse review = reviewAnnotation(annotations.get(i).getId(), null);
                reviewedIds.add(review.getObject().getId());
            }


        }
        taskService.finishTask(task);
        return reviewedIds;
    }

    public List<Long> unreviewLayer(Long imageInstanceId, List<Long> usersIds, Task task) {

        taskService.updateTask(task,2,"Extract parameters...");
        List<SecUser> users = usersIds.stream()
                .map(x -> userRepository.findById(x).orElseThrow(() -> new ObjectNotFoundException("User", x))).collect(Collectors.toList());
        ImageInstance imageInstance = imageInstanceService.find(imageInstanceId)
                .orElseThrow(() -> new ObjectNotFoundException("ImageInstance", imageInstanceId));

        //check constraint
        taskService.updateTask(task,3,"Review "+users.size()+" layers...");
        if(!imageInstance.isInReviewMode()) {
            throw new WrongArgumentException("Cannot reject annotation, enable image review mode!");
        } else if(imageInstance.getReviewUser()!=null && !Objects.equals(imageInstance.getReviewUser().getId(), currentUserService.getCurrentUser().getId())) {
            throw new WrongArgumentException("You must be the image reviewer to reject annotation. Image reviewer is " + imageInstance.getReviewUser().getUsername());
        } else if(users.isEmpty()) {
            throw new WrongArgumentException("There is no layer:"+usersIds);
        }

        List<AnnotationDomain> annotations = new ArrayList<>();
        taskService.updateTask(task,5,"Look for all annotations...");
        for (SecUser user : users) {
            if (user.isAlgo()) {
                throw new CytomineMethodNotYetImplementedException(""); // TODO
            } else {
                annotations.addAll(userAnnotationRepository.findAllByUserAndImage((User)user, imageInstance));
            }
        }

        //unreview each one
        taskService.updateTask(task,10,annotations.size() + " annotations found...");
        int realUnReviewed = 0;
        int taskRefresh =  annotations.size()>1000? 100 : 10;

        List<ReviewedAnnotation> reviewed = new ArrayList<>();
        for(int i = 0; i < annotations.size(); i++) {
            if (i%taskRefresh==0) {
                taskService.updateTask(task,10+(int)(((double)i/(double)annotations.size())*0.9d*100),realUnReviewed + " new reviewed annotations...");
            }
            Optional<ReviewedAnnotation> optionalReviewedAnnotation = reviewedAnnotationRepository.findByParentIdent(annotations.get(i).getId());

            if (optionalReviewedAnnotation.isPresent()) {
                realUnReviewed++;
                reviewed.add(optionalReviewedAnnotation.get());
            }


        }

        if (!reviewed.isEmpty()) {
            reviewedAnnotationRepository.deleteAll(reviewed);
        }



        taskService.finishTask(task);
        return reviewed.stream().map(CytomineDomain::getId).collect(Collectors.toList());
    }























    protected void beforeAdd(CytomineDomain domain) {
        // this will be done in the PrePersist method ; but the validation is done before PrePersist
        ((ReviewedAnnotation)domain).setWktLocation(((ReviewedAnnotation)domain).getLocation().toText());
    }


    public List<Object> getStringParamsI18n(CytomineDomain domain) {
        ReviewedAnnotation annotation = (ReviewedAnnotation)domain;
        return List.of(currentUserService.getCurrentUser().toString(), annotation.getImage().getBaseImage().getOriginalFilename());
    }



    @Override
    public void checkDoNotAlreadyExist(CytomineDomain domain) {
        // TODO: with new session?
        Optional<ReviewedAnnotation> annotationAlreadyExist = reviewedAnnotationRepository.findByParentIdent(((ReviewedAnnotation)domain).getParentIdent());
        if (annotationAlreadyExist.isPresent() && (!Objects.equals(annotationAlreadyExist.get().getId(), domain.getId()))) {
            throw new AlreadyExistException("Annotation " + ((ReviewedAnnotation)domain).getParentIdent() + " has already been reviewed");
        }
    }


    public void deleteDependencies(CytomineDomain domain, Transaction transaction, Task task) {
        ((ReviewedAnnotation)domain).getTerms().clear();
    }



//TODO
//def deleteDependentAlgoAnnotationTerm(ReviewedAnnotation annotation, Transaction transaction, Task task = null) {
//    AlgoAnnotationTerm.findAllByAnnotationIdentAndDeletedIsNull(annotation.id).each {
//        algoAnnotationTermService.delete(it,transaction,null,false)
//    }
//}



}
