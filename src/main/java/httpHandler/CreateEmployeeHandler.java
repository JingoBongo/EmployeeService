package httpHandler;

import HttpClient.HttpUtility;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import main.MainRunner;
import main.SingleProcessValues;
import mongo.MongoHandler;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;



public class CreateEmployeeHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        try {
            //  check if this is GET request
            if("GET".equals(httpExchange.getRequestMethod())) {
                handleGetResponse(httpExchange);
                return;
            }

            //  otherwise, request must have payload in its body
            String requestBody;
            //  try to get payload from request body
            if ((requestBody = getRequestPayload(httpExchange)) == null) {
                System.err.println("Couldn't take content of request");
                return;
            }

            //  handle request basing on type of request
            if("POST".equals(httpExchange.getRequestMethod())) {
                handlePostResponse(httpExchange, requestBody);
            } else if("PUT".equals(httpExchange.getRequestMethod())) {
                handlePutResponse(httpExchange, requestBody);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void handlePutResponse(HttpExchange httpExchange, String requestPayload) throws IOException {
        //here I need to get what exact parameter is added, add needed param to item in map, change status to euevupdated : UPD: change status to building
        try {
            //  start deserialization of json payload
            ObjectMapper objectMapper = new ObjectMapper();
            JSONObject jsonObject = new JSONObject();
            ObjectNode car = objectMapper.readValue(requestPayload, ObjectNode.class);

            //  check if such process exists in storage
            if (MainRunner.getRequestsMap().containsKey(car.get("id").asText())) {
                //  get existing process from storage
                String id = car.get("id").asText();
                if(!MainRunner.getRequestsMap().get(id).getStatus().equals("created")){
                    jsonObject.put("id", id);
                    jsonObject.put("status", "denied");
                    jsonObject.put("reason", "You can only request a PUT when your id process has status 'created', but it has status '"+MainRunner.getRequestsMap().get(id).getStatus()+"'");
                    String payload = jsonObject.toString();
                    sendResponse(httpExchange, payload);
                    return;
                }
                String role3 = car.get("role").asText();
                String location = car.get("location").asText();
                MainRunner.getRequestsMap().get(id).setRole(role3);
                MainRunner.getRequestsMap().get(id).setLocation(location);
                MainRunner.getRequestsMap().get(id).setStatus("populated");


                //  make json-formatted string
                jsonObject.put("id", id);
                jsonObject.put("status", MainRunner.getRequestsMap().get(id).getStatus());
                String payload = jsonObject.toString();

                sendResponse(httpExchange, payload);
            } else {
                System.err.println(MainRunner.getCurTime()+"there is no such process when getting PUT request: "+car.get("id").asText());

                jsonObject.put("id", car.get("id").asText());
                jsonObject.put("status", "denied");
                jsonObject.put("reason", "id not found");
                String payload = jsonObject.toString();
                sendResponse(httpExchange, payload);
                //send error. depending on reason
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void handlePostResponse(HttpExchange httpExchange, String requestPayload)  throws  IOException {
        //post is very first request. new item in list is created,
        try {
            //  start deserialization of json payload
            ObjectMapper objectMapper = new ObjectMapper();
            JSONObject jsonObject = new JSONObject();
            ObjectNode car = objectMapper.readValue(requestPayload, ObjectNode.class);

            if(httpExchange.getRequestHeaders().containsKey("Service-Call"))
                if(httpExchange.getRequestHeaders().get("Service-Call").get(0).startsWith("broadcast")){
                    //what are the tasks? rollback - for entire name
                    //                    syncName
                    //                    syncInfo
                    switch (httpExchange.getRequestHeaders().get("task").get(0)){
                        case "getEmployeeRole":
                            String name2 = car.get("name").asText();
                            String roleMongoResp = MongoHandler.findRoleByName(name2);
                            //segment to check result
                            //if contains name, but no role, then return 'no role, cant create'
                            //if no name no role, return 'no such employee, cant create'
                            //if both, extract and send role

                            if(roleMongoResp.contains(name2) && roleMongoResp.contains("role")){
                                //there is a role, send back
                                ObjectMapper objectMapperForGetEmpl = new ObjectMapper();
                                ObjectNode car2 = objectMapperForGetEmpl.readValue(roleMongoResp, ObjectNode.class);
                                String role =car2.get("role").asText().replaceAll("\"", "");

                                System.out.println(MainRunner.getCurTime()+"Sending role "+role+" for "+name2+" user from "+MainRunner.dbCollectionName);

                                jsonObject.put("role", role);
                                jsonObject.put("status","ok");
                                String payload = jsonObject.toString();
                                sendResponse(httpExchange, payload);
                            } else if(roleMongoResp.contains(name2) && !roleMongoResp.contains("role")){
                                //with no role, send no role
                                System.out.println(MainRunner.getCurTime()+"No such role for "+name2+" user is in "+MainRunner.dbCollectionName);
                                jsonObject.put("status","error");
                                jsonObject.put("reason",name2+" has no role yet");
                                String payload = jsonObject.toString();
                                sendResponse(httpExchange, payload);
                            } else {
                                //nothing, send no such user
                                System.out.println(MainRunner.getCurTime()+"No such user with "+name2+" is in "+MainRunner.dbCollectionName);
                                jsonObject.put("status","error");
                                jsonObject.put("reason",name2+" doesn't exist");
                                String payload = jsonObject.toString();
                                sendResponse(httpExchange, payload);
                            }
                            break;

                        case "rollback":
                            //not needed yet. this is in case roll back of sync needed
                            //our roll backs are logic related, therefore don't go out of 1 service
                            //for major db sync problem use failover
                            break;
                        case "syncName":
                            String name = car.get("name").asText();
                            String id = car.get("id").asText();
                            if(!MongoHandler.checkIfExistsByName(name)){
                                MongoHandler.insertIntoMongo(id, name);
                                if(MongoHandler.checkIfExistsByName(name)){
                                    //return ok
                                    System.out.println(MainRunner.getCurTime()+"Synced name in db "+MainRunner.dbCollectionName);
                                    jsonObject.put("status","ok");
                                    jsonObject.put("reason", id +" synced in db "+MainRunner.dbCollectionName);
                                    String payload = jsonObject.toString();
                                    sendResponse(httpExchange, payload);
                                } else {
                                    //return
                                    System.out.println(MainRunner.getCurTime()+"Not synced name in db "+MainRunner.dbCollectionName);
                                    jsonObject.put("status","error");
                                    jsonObject.put("reason", id +" is not synced in db "+MainRunner.dbCollectionName);
                                    String payload = jsonObject.toString();
                                    sendResponse(httpExchange, payload);
                                    //ignore
                                }
                            } else {
                                System.out.println(MainRunner.getCurTime()+"couldn't sync "+id+", there is no such employee in db "+MainRunner.dbCollectionName);
                                jsonObject.put("status","error");
                                jsonObject.put("reason", "couldn't sync "+id+", there is no such employee in db "+MainRunner.dbCollectionName);
                                String payload = jsonObject.toString();
                                sendResponse(httpExchange, payload);
                            }
                            break;

                        case "syncInfo":
                            String id2 = car.get("id").asText();
                            String role = car.get("role").asText();
                            String location = car.get("location").asText();
                            if(!MongoHandler.checkIfExistsById(id2)) {
                                MongoHandler.updateInMongo(id2, role, location);
                                if (MongoHandler.checkIfExistsById(id2)) {
                                    //return ok
                                    System.out.println(MainRunner.getCurTime()+"Synced info in db "+MainRunner.dbCollectionName);
                                    jsonObject.put("status", "ok");
                                    jsonObject.put("reason", id2 + " synced.");
                                    String payload = jsonObject.toString();
                                    sendResponse(httpExchange, payload);
                                } else {
                                    //return
                                    //ignore. фдьщые
                                    System.out.println(MainRunner.getCurTime()+"Not synced info in db "+MainRunner.dbCollectionName);
                                    jsonObject.put("status","error");
                                    jsonObject.put("reason", id2 +" doesn't exist in db "+MainRunner.dbCollectionName);
                                    String payload = jsonObject.toString();
                                    sendResponse(httpExchange, payload);
                                }
                            }else {
                                System.out.println(MainRunner.getCurTime()+"couldn't sync "+id2+", there is no such employee in db "+MainRunner.dbCollectionName);
                                jsonObject.put("status","error");
                                jsonObject.put("reason", "couldn't sync "+id2+", there is no such employee in db "+MainRunner.dbCollectionName);
                                String payload = jsonObject.toString();
                                sendResponse(httpExchange, payload);
                            }
                            break;
                        default:
                            System.out.println(MainRunner.getCurTime()+"Caught unknown task: "+httpExchange.getRequestHeaders().get("task").get(0)+" in "+MainRunner.serviceName+":"+MainRunner.servicePort);
                    }

                    return;
                }

            //  start process with taken from JSON command name
            String name = car.get("name").toString().replaceAll("\"", "");

            //check if name exists in mongo
            if(MongoHandler.checkIfExistsByName(name)){
                jsonObject.put("status","denied");
                jsonObject.put("reason",name+" already exists.");
                String payload = jsonObject.toString();
                sendResponse(httpExchange, payload);
                return;
            }
            //nah. create just as local var. for now
            //
            SingleProcessValues newProcess = new SingleProcessValues(name);
            MainRunner.getRequestsMap().put(newProcess.id, newProcess);
            MainRunner.getRequestsMap().get(newProcess.id).setStatus("created");
            MongoHandler.insertIntoMongo(newProcess.id, name);
            HttpUtility.syncCreation(newProcess.id, name);

            jsonObject.put("id", newProcess.id);
            jsonObject.put("status", "created");
            String payload = jsonObject.toString();
            sendResponse(httpExchange, payload);

        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private void handleGetResponse(HttpExchange httpExchange) throws IOException {
        //after finalize, this will return
        try {
            //  get from uri required process ID
            JSONObject jsonObject = new JSONObject();

            long requestedIndex = Long.parseLong(httpExchange.getRequestURI().toString().
                    split("\\?")[1].
                    split("=")[1]);

            //  check if there is such id
            if (MainRunner.getRequestsMap().containsKey(String.valueOf(requestedIndex))) {
                //  get requested id
                String id = String.valueOf(requestedIndex);

                //  if process is not ready for transmission then inform client about it
                if (!MainRunner.getRequestsMap().get(id).getStatus().equals("populated")) {
//                    System.err.println("process has not received all required arguments");
                    //  inform client that process is unfinished
//                    String response = "{\"response\":undone}";
//                    sendResponse(httpExchange, response);
                    jsonObject.put("id", id);
                    jsonObject.put("status", "denied");
                    jsonObject.put("reason", "You can only request a GET when your id process has status 'populated', but it has status '"+MainRunner.getRequestsMap().get(id).getStatus()+"'");
                    String payload = jsonObject.toString();
                    sendResponse(httpExchange, payload);
                    return;
                }

                //  set status of process to "done"

                //ACTUALLY make request
                // and update mongo row
                String name = MainRunner.getRequestsMap().get(id).getName();
                String role = MainRunner.getRequestsMap().get(id).getRole();
                String location = MainRunner.getRequestsMap().get(id).getLocation();
                MongoHandler.updateInMongo(id, role, location);
                HttpUtility.syncUpdate(id, role, location);
                MainRunner.getRequestsMap().get(id).setStatus("ready");

                jsonObject.put("id", id);
                jsonObject.put("status", MainRunner.getRequestsMap().get(id).getStatus());
                jsonObject.put("name", name);
                jsonObject.put("role", role);
                jsonObject.put("location", location);
                String payload = jsonObject.toString();
                sendResponse(httpExchange, payload);

            } else {
                System.err.println(MainRunner.getCurTime()+"there is no such process when getting GET request: "+requestedIndex);
                jsonObject.put("id", requestedIndex);
                jsonObject.put("status", "denied");
                jsonObject.put("reason", "id not found");
                String payload = jsonObject.toString();
                sendResponse(httpExchange, payload);
            }

        }catch (Exception e){
            e.printStackTrace();
        }
    }





    private String getRequestPayload(HttpExchange httpExchange) throws IOException {
        //  check that there is specified content type of request
        if(httpExchange.getRequestHeaders().containsKey("Content-Type")) {
            //  check content to be equal to json formatted data
            if(httpExchange.getRequestHeaders().get("Content-Type").get(0).equals("application/json")){
                //  open stream for getting UTF-8 formatted characters
                InputStreamReader inputStreamReader = new InputStreamReader(httpExchange.getRequestBody(), StandardCharsets.UTF_8);

                //  insert stream to buffer for reading through input stream
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

                //  initialize current character and buffer for appending all incoming characters
                int currentCharacter;
                StringBuilder buffer = new StringBuilder(512);

                //  while it's not the end of all stream, read char-by-char all incoming data
                while((currentCharacter = bufferedReader.read()) != -1) {
                    buffer.append((char) currentCharacter);
                }

                //  close buffer and input stream
                bufferedReader.close();
                inputStreamReader.close();

                //  return string-formatted data
                return buffer.toString();
            } else {
                System.err.println("Unknown content-type");
            }
        } else {
            System.err.println("No content-type specified");
        }
        return null;
    }

    private void sendResponse(HttpExchange httpExchange, String response) throws IOException {
        //  set headers of response
        httpExchange.getResponseHeaders().set("Content-Type", "application/json");
        httpExchange.getResponseHeaders().set("Content-Length", String.valueOf(response.length()));
        httpExchange.sendResponseHeaders(200, response.length());
        System.out.println(MainRunner.getCurTime()+response);

        //  send response to the client
        OutputStream outputStream = httpExchange.getResponseBody();
        System.out.println(MainRunner.getCurTime()+"Sending response");
        outputStream.write(response.getBytes(), 0, response.length());
        outputStream.flush();
//        outputStream.close();
    }

//    private void sendResponseGet(HttpExchange httpExchange, String response) throws IOException {
//
//        //  set headers of response
//        httpExchange.getResponseHeaders().set("Content-Type", "application/json");
//        httpExchange.getResponseHeaders().set("Content-Length", String.valueOf(response.length()));
//        httpExchange.sendResponseHeaders(200, response.length());
//        System.out.println(response);
//
//        //  send response to the client
//        OutputStream outputStream = httpExchange.getResponseBody();
//        System.out.println(response.length());
////        outputStream.write(response.getBytes(), 0, response.length());
//        outputStream.write(response.getBytes());
//        outputStream.flush();
////        outputStream.close();
//    }

}
