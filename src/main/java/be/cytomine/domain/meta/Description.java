package be.cytomine.domain.meta;

import be.cytomine.domain.CytomineDomain;
import be.cytomine.domain.GenericCytomineDomainContainer;
import be.cytomine.utils.JsonObject;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Entity
@Getter
@Setter
public class Description extends CytomineDomain {

    @NotNull
    private String data;

    @NotNull
    @NotBlank
    private String domainClassName;

    @NotNull
    private Long domainIdent;


    public void setDomain(CytomineDomain domain) {
        domainClassName = domain.getClass().getName();
        domainIdent = domain.getId();
    }

    public CytomineDomain buildDomainFromJson(JsonObject json, EntityManager entityManager) {
        Description description = this;
        description.id = json.getJSONAttrLong("id",null);

        Long id = json.getJSONAttrLong("domainIdent",-1l);
        if (id == -1) {
            id = json.getJSONAttrLong("domain",-1l);
        }
        description.domainIdent = id;
        description.domainClassName = json.getJSONAttrStr("domainClassName", true);
        description.data = json.getJSONAttrStr("data", true);
        description.created = json.getJSONAttrDate("created");
        description.updated = json.getJSONAttrDate("updated");
        return description;
    }

    public static JsonObject getDataFromDomain(CytomineDomain domain) {
        JsonObject returnArray = CytomineDomain.getDataFromDomain(domain);
        Description description = (Description)domain;
        returnArray.put("domainIdent", description.getDomainIdent());
        returnArray.put("domainClassName", description.getDomainClassName());
        returnArray.put("data", description.getData());
        return returnArray;
    }

    @Override
    public String toJSON() {
        return toJsonObject().toJsonString();
    }

    @Override
    public JsonObject toJsonObject() {
        return getDataFromDomain(this);
    }

    public CytomineDomain container() {
        GenericCytomineDomainContainer genericCytomineDomainContainer = new GenericCytomineDomainContainer();
        genericCytomineDomainContainer.setId(domainIdent);
        genericCytomineDomainContainer.setContainerClass(domainClassName);
        return genericCytomineDomainContainer;
    }
}
