/*
*  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.charon3.core.protocol;

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.wso2.charon3.core.exceptions.BadRequestException;
import org.wso2.charon3.core.extensions.RoleManager;
import org.wso2.charon3.core.extensions.UserManager;
import org.wso2.charon3.core.objects.bulk.BulkRequestContent;
import org.wso2.charon3.core.objects.bulk.BulkRequestData;
import org.wso2.charon3.core.objects.bulk.BulkResponseContent;
import org.wso2.charon3.core.objects.bulk.BulkResponseData;
import org.wso2.charon3.core.protocol.endpoints.GroupResourceManager;
import org.wso2.charon3.core.protocol.endpoints.ResourceManager;
import org.wso2.charon3.core.protocol.endpoints.RoleResourceManager;
import org.wso2.charon3.core.protocol.endpoints.UserResourceManager;
import org.wso2.charon3.core.schema.SCIMConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class BulkRequestProcessor {
    private UserResourceManager userResourceManager;
    private GroupResourceManager groupResourceManager;
    private RoleResourceManager roleResourceManager;
    private int failOnError;
    private int errors;
    private UserManager userManager;
    private RoleManager roleManager;


    public UserResourceManager getUserResourceManager() {
        return userResourceManager;
    }

    public void setUserResourceManager(UserResourceManager userResourceManager) {
        this.userResourceManager = userResourceManager;
    }

    public GroupResourceManager getGroupResourceManager() {
        return groupResourceManager;
    }

    public void setGroupResourceManager(GroupResourceManager groupResourceManager) {
        this.groupResourceManager = groupResourceManager;
    }

    public RoleResourceManager getRoleResourceManager() {

        return roleResourceManager;
    }

    public void setRoleResourceManager(RoleResourceManager roleResourceManager) {

        this.roleResourceManager = roleResourceManager;
    }

    public int getFailOnError() {
        return failOnError;
    }

    public void setFailOnError(int failOnError) {
        this.failOnError = failOnError;
    }

    public int getErrors() {
        return errors;
    }

    public void setErrors(int errors) {
        this.errors = errors;
    }

    public UserManager getUserManager() {
        return userManager;
    }

    public void setUserManager(UserManager userManager) {
        this.userManager = userManager;
    }

    public RoleManager getRoleManager() {

        return roleManager;
    }

    public void setRoleManager(RoleManager roleManager) {

        this.roleManager = roleManager;
    }

    public BulkRequestProcessor() {

        userResourceManager = new UserResourceManager();
        groupResourceManager = new GroupResourceManager();
        roleResourceManager = new RoleResourceManager();
        failOnError = 0;
        errors = 0;
        userManager = null;
        roleManager = null;
    }

    public BulkResponseData processBulkRequests(BulkRequestData bulkRequestData) throws BadRequestException {

        BulkResponseData bulkResponseData = new BulkResponseData();

        for (BulkRequestContent bulkRequestContent : bulkRequestData.getUserOperationRequests()) {
            if (failOnError == 0) {
                bulkResponseData.addUserOperation
                            (getBulkResponseContent(bulkRequestContent, userResourceManager));
            } else {
                if (errors < failOnError) {
                    bulkResponseData.addUserOperation
                            (getBulkResponseContent(bulkRequestContent, userResourceManager));
                }
            }

        }
        Map<String, String> userIdMappings = getUserIdBulkIdMapping(bulkResponseData.getUserOperationResponse());

        for (BulkRequestContent bulkRequestContent : bulkRequestData.getGroupOperationRequests()) {
            if (failOnError == 0) {
                bulkResponseData.addGroupOperation
                            (getBulkResponseContent(bulkRequestContent,
                                    userIdMappings, groupResourceManager));
            } else  {
                if (errors < failOnError) {
                    bulkResponseData.addGroupOperation
                            (getBulkResponseContent(bulkRequestContent,
                                    userIdMappings, groupResourceManager));
                }
            }

        }
        for (BulkRequestContent bulkRequestContent : bulkRequestData.getRoleOperationRequests()) {
            if (failOnError == 0) {
                bulkResponseData.addRoleOperation(getBulkResponseContent(bulkRequestContent, userIdMappings,
                        roleResourceManager));
            } else {
                if (errors < failOnError) {
                    bulkResponseData.addRoleOperation(getBulkResponseContent(bulkRequestContent,
                            userIdMappings, roleResourceManager));
                }
            }
        }
        bulkResponseData.setSchema(SCIMConstants.BULK_RESPONSE_URI);
        return bulkResponseData;
    }

    private BulkResponseContent getBulkResponseContent(BulkRequestContent bulkRequestContent,
                                                       ResourceManager resourceManager) throws BadRequestException {

        return getBulkResponseContent(bulkRequestContent, null, resourceManager);
    }

   private BulkResponseContent getBulkResponseContent(BulkRequestContent bulkRequestContent,
                                                      Map<String, String> userIdMappings,
                                                      ResourceManager resourceManager)
           throws BadRequestException {

       BulkResponseContent bulkResponseContent = null;
       SCIMResponse response;
       processBulkRequestContent(bulkRequestContent, userIdMappings, bulkRequestContent.getMethod());

       switch (bulkRequestContent.getMethod()) {
           case SCIMConstants.OperationalConstants.POST:
               if (bulkRequestContent.getPath().contains(SCIMConstants.ROLE_ENDPOINT)) {
                   response = resourceManager.createRole(bulkRequestContent.getData(), roleManager);
               } else {
                   response = resourceManager.create(bulkRequestContent.getData(), userManager,
                           null, null);
               }

               bulkResponseContent = createBulkResponseContent(response, SCIMConstants.OperationalConstants.POST,
                       bulkRequestContent);
               errorsCheck(response);
               break;

           case SCIMConstants.OperationalConstants.PUT: {
               String resourceId = extractIDFromPath(bulkRequestContent.getPath());
               if (bulkRequestContent.getPath().contains(SCIMConstants.ROLE_ENDPOINT)) {
                   response = resourceManager.updateWithPUTRole(resourceId, bulkRequestContent.getData(),
                           roleManager);
               } else {
                   response = resourceManager.updateWithPUT(resourceId, bulkRequestContent.getData(), userManager,
                                   null, null);
               }

               bulkResponseContent = createBulkResponseContent(response, SCIMConstants.OperationalConstants.PUT,
                       bulkRequestContent);
               errorsCheck(response);
               break;
           }

           case SCIMConstants.OperationalConstants.PATCH: {
               String resourceId = extractIDFromPath(bulkRequestContent.getPath());
               if (bulkRequestContent.getPath().contains(SCIMConstants.ROLE_ENDPOINT)) {
                   response = resourceManager.updateWithPATCHRole(resourceId, bulkRequestContent.getData(),
                           roleManager);
               } else {
                   response = resourceManager.updateWithPATCH(resourceId, bulkRequestContent.getData(), userManager,
                                   null, null);
               }

               bulkResponseContent = createBulkResponseContent(response, SCIMConstants.OperationalConstants.PATCH,
                       bulkRequestContent);
               errorsCheck(response);
               break;
           }

           case SCIMConstants.OperationalConstants.DELETE: {
               String resourceId = extractIDFromPath(bulkRequestContent.getPath());
               if (bulkRequestContent.getPath().contains(SCIMConstants.ROLE_ENDPOINT)) {
                   response = resourceManager.deleteRole(resourceId, roleManager);
               } else {
                   response = resourceManager.delete(resourceId, userManager);
               }

               bulkResponseContent = createBulkResponseContent(response, SCIMConstants.OperationalConstants.DELETE,
                       bulkRequestContent);
               errorsCheck(response);
               break;
           }
       }
       return bulkResponseContent;
   }

    private String extractIDFromPath(String path) throws BadRequestException {

        String [] parts = path.split("[/]");
        if (parts[2] != null) {
            return parts[2];
        } else {
            throw new BadRequestException
                    ("No resource Id is provided in path", ResponseCodeConstants.INVALID_VALUE);
        }
    }

    private BulkResponseContent createBulkResponseContent(SCIMResponse response, String method,
                                                          BulkRequestContent requestContent) {

        BulkResponseContent bulkResponseContent = new BulkResponseContent();
        bulkResponseContent.setScimResponse(response);
        bulkResponseContent.setMethod(method);
        if (response.getHeaderParamMap() != null) {
            bulkResponseContent.setLocation(response.getHeaderParamMap().get(SCIMConstants.LOCATION_HEADER));
        }
        bulkResponseContent.setBulkID(requestContent.getBulkID());
        bulkResponseContent.setVersion(requestContent.getVersion());

        return bulkResponseContent;
    }

    private void errorsCheck(SCIMResponse response) {
        if (response.getResponseStatus() != 200 && response.getResponseStatus() != 201 &&
                response.getResponseStatus() != 204) {
            errors++;
        }
    }

    /**
     * This method is used to process the bulk request content.
     * This method will replace the bulk id with the created user id.
     *
     * @param bulkRequestContent   Bulk request content.
     * @param userIDMappings       User id bulk id mapping.
     * @param method               HTTP method.
     * @throws BadRequestException Bad request exception.
     */
    private void processBulkRequestContent(BulkRequestContent bulkRequestContent, Map<String, String> userIDMappings,
                                           String method) throws BadRequestException {

        try {
            if (userIDMappings == null || userIDMappings.isEmpty()
                    || method.equals(SCIMConstants.OperationalConstants.DELETE)) {
                return;
            }
            // Parse the data field to a JSON object.
            JSONObject dataJson = new JSONObject(bulkRequestContent.getData());
            String usersOrMembersKey = getUsersOrMembersKey(bulkRequestContent.getPath());
            JSONArray usersArray = getUserArray(dataJson, method, usersOrMembersKey);

            if (usersArray != null) {
                String bulkIdPrefix = SCIMConstants.OperationalConstants.BULK_ID + ":";
                for (int i = 0; i < usersArray.length(); i++) {
                    JSONObject user = usersArray.getJSONObject(i);
                    String userValue = user.getString(SCIMConstants.OperationalConstants.VALUE);
                    if (userValue.startsWith(bulkIdPrefix)) {
                        String userBulkId = userValue.substring(bulkIdPrefix.length());
                        String userId = userIDMappings.get(userBulkId);
                        if (StringUtils.isNotBlank(userId)) {
                            user.put(SCIMConstants.OperationalConstants.VALUE, userId);
                        }
                    }
                }
            }
            bulkRequestContent.setData(dataJson.toString());

        } catch (JSONException e) {
            throw new BadRequestException("Error while parsing the data field of the bulk request content",
                    ResponseCodeConstants.INVALID_SYNTAX);
        }
    }

    private String getUsersOrMembersKey(String path) {

        return path.contains(SCIMConstants.ROLE_ENDPOINT) ? SCIMConstants.RoleSchemaConstants.USERS :
                SCIMConstants.GroupSchemaConstants.MEMBERS;
    }

    /**
     * This method is used to get the user array from the data JSON object.
     *
     * @param dataJson          SCIM data JSON object.
     * @param method            HTTP method.
     * @param usersOrMembersKey Users or members key.
     * @return User array
     * @throws JSONException    JSON exception.
     */
    private JSONArray getUserArray(JSONObject dataJson, String method, String usersOrMembersKey)
            throws JSONException {

        switch (method) {
            case SCIMConstants.OperationalConstants.POST:
            case SCIMConstants.OperationalConstants.PUT:
                return dataJson.optJSONArray(usersOrMembersKey);
            case SCIMConstants.OperationalConstants.PATCH:
                return getUserArrayForPatch(dataJson, usersOrMembersKey);
            default:
                return null;
        }
    }

    private JSONArray getUserArrayForPatch(JSONObject dataJson, String usersOrMembersKey) throws JSONException {

        JSONArray operations = dataJson.optJSONArray(SCIMConstants.OperationalConstants.OPERATIONS);
        if (operations != null) {
            for (int i = 0; i < operations.length(); i++) {
                JSONObject operation = operations.getJSONObject(i);
                if (isAddOperation(operation, usersOrMembersKey)) {
                    if (operation.has(SCIMConstants.OperationalConstants.PATH)) {
                        return operation.optJSONArray(SCIMConstants.OperationalConstants.VALUE);
                    } else {
                        JSONObject valueObject = operation.optJSONObject(SCIMConstants.OperationalConstants.VALUE);
                        if (valueObject != null) {
                            return valueObject.optJSONArray(usersOrMembersKey);
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isAddOperation(JSONObject operation, String path) throws JSONException {

        String operationType = operation.optString(SCIMConstants.OperationalConstants.OP);
        String operationPath = operation.optString(SCIMConstants.OperationalConstants.PATH, "");

        return SCIMConstants.OperationalConstants.ADD.equalsIgnoreCase(operationType) &&
                (operationPath.equals(path) || operationPath.isEmpty());
    }

    /**
     * This method is used to get user id bulk id mapping from the bulk user operation response.
     *
     * @param bulkUserOperationResponse Bulk user operation response.
     * @return Bulk id user id mapping.
     */
    private static Map<String, String> getUserIdBulkIdMapping(List<BulkResponseContent> bulkUserOperationResponse) {

        Map<String, String> userIdMappings = new HashMap<>();
        for (BulkResponseContent bulkResponse : bulkUserOperationResponse) {
            String bulkId = bulkResponse.getBulkID();

            SCIMResponse response = bulkResponse.getScimResponse();
            if (response.getResponseStatus() == ResponseCodeConstants.CODE_CREATED) {
                String locationHeader = response.getHeaderParamMap().get(SCIMConstants.LOCATION_HEADER);

                if (locationHeader != null) {
                    String[] locationHeaderParts = locationHeader.split("/");
                    String userId = locationHeaderParts[locationHeaderParts.length - 1];
                    userIdMappings.put(bulkId, userId);
                }
            }
        }
        return userIdMappings;
    }
}
