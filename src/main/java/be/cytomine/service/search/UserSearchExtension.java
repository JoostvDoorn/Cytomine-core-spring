package be.cytomine.service.search;

/*
* Copyright (c) 2009-2022. Authors: see NOTICE file.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserSearchExtension {
    private boolean withRoles;

    private boolean withLastImage;

    private boolean withLastConnection;

    private boolean withNumberConnections;

    private boolean withUserJob;

    public static UserSearchExtension onlyWithRoles() {
        UserSearchExtension userSearchExtension = new UserSearchExtension();
        userSearchExtension.setWithRoles(true);
        return userSearchExtension;
    }

    public boolean noExtension() {
        return !withRoles && !withLastImage && !withLastConnection && !withNumberConnections && !withUserJob;
    }
}
