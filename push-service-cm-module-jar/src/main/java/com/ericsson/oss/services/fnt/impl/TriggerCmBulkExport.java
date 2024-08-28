/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2021
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.fnt.impl;

import com.ericsson.commonlibrary.httpclient.common.HttpRequestBuilder;
import com.ericsson.commonlibrary.httpclient.common.HttpRequestListener;
import com.ericsson.commonlibrary.httpclient.http1.HttpClient;
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender;
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled;
import com.ericsson.oss.itpf.sdk.recording.EventLevel;
import com.ericsson.oss.itpf.sdk.recording.SystemRecorder;
import com.ericsson.oss.services.fnt.Exception.PushServiceException;
import com.ericsson.oss.services.fnt.cmrequests.CmBulkExportRequest;
import com.ericsson.oss.services.fnt.cmrequests.dailyrequests.AllNodesNoFilterRequest;
import com.ericsson.oss.services.fnt.cmrequests.dailyrequests.SwFilterNetworkElementRequest;
import com.ericsson.oss.services.fnt.cmrequests.dailyrequests.TcimNoFilterRequest;
import com.ericsson.oss.services.fnt.cmrequests.weeklyrequests.HwFilterBscRequest;
import com.ericsson.oss.services.fnt.cmrequests.weeklyrequests.HwFilterBspRequest;
import com.ericsson.oss.services.fnt.cmrequests.weeklyrequests.HwFilterEquipmentRequest;
import com.ericsson.oss.services.fnt.cmrequests.weeklyrequests.HwFilterInventoryWeeklyRequest;
import com.ericsson.oss.services.fnt.cmrequests.weeklyrequests.HwFilterRadioRequest;
import com.ericsson.oss.services.fnt.cmrequests.weeklyrequests.HwFilterSupportUnitRequest;
import com.ericsson.oss.services.fnt.cmrequests.weeklyrequests.LicFilterFeatureKeyRequest;
import com.ericsson.oss.services.fnt.cmrequests.weeklyrequests.LicFilterInventoryCapacityKeyRequest;
import com.ericsson.oss.services.fnt.cmrequests.weeklyrequests.LicFilterInventoryElectronicKeyRequest;
import com.ericsson.oss.services.fnt.event.FilePushEvent;
import com.ericsson.oss.services.fnt.utils.HttpClientUtility;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.handler.codec.http.HttpScheme;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class TriggerCmBulkExport {

    @Inject
    HttpClientUtility httpClientUtility;

    @Inject
    private SystemRecorder systemRecorder;

    @Inject
    @Modeled
    EventSender<FilePushEvent> eventEventSender;

    @Inject
    CmRequestStatusCache cmRequestStatusCache;

    private static final Logger logger = LoggerFactory.getLogger(TriggerCmBulkExport.class);

    private static final String IMPORT_EXPORT_SERVICE = "cmutilities-service";
    private static final int IMPORT_EXPORT_PORT = 8100;
    private static final String BULK_EXPORT_URL = "/bulk/export/jobs";

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String USER_ID = "X-Tor-UserID";
    private static final String ADMINISTRATOR = "administrator";

    private static final String JOB_NAME_BULK_FILTER_1 = "bulk1";
    private static final String JOB_NAME_SW_FILTER_1 = "SW1";
    private static final String JOB_NAME_HW_FILTER_1 = "HW1";
    private static final String JOB_NAME_HW_FILTER_2 = "HW2";
    private static final String JOB_NAME_HW_FILTER_3 = "HW3";
    private static final String JOB_NAME_HW_FILTER_4 = "HW4";
    private static final String JOB_NAME_HW_FILTER_5 = "HW5";
    private static final String JOB_NAME_HW_FILTER_6 = "HW6";
    private static final String JOB_NAME_LIC_FILTER_1 = "LIC1";
    private static final String JOB_NAME_LIC_FILTER_2 = "LIC2";
    private static final String JOB_NAME_LIC_FILTER_3 = "LIC3";
    private static final String JOB_NAME_TCIM_FILTER_1 = "TCIM1";

    private static final String DELIMITER = "-";

    private static final String JOB_ID = "id";
    private static final String JOB_STATUS = "status";
    private static final String STATUS_REASON = "statusReason";
    private static final String NODES_EXPORTED = "nodesExported";
    private static final String NODES_NOT_EXPORTED = "nodesNotExported";
    private static final String NODES_NO_MATCH_FOUND = "nodesNoMatchFound";
    private static final String NO_OF_MO_EXPORTED = "numberOfMosExported";
    private static final String JOB_STATUS_COMPLETED = "COMPLETED";
    private static final String JOB_STATUS_FAILED = "FAILED";
    private static final String FILE_FORMAT = ".zip";
    private static final String FILE_PATH = "ericsson/batch/data/export/3gpp_export/";
    private static final String SLASH = "/";

    private HttpRequestListener response;

    private String jobName;

    private List<CmBulkExportRequest> allRequestsList = new ArrayList<>();

    FilePushEvent cmFileCollectionEvent = new FilePushEvent();

    private String destinationUserName;

    public int getRequestsListSize() {
        return allRequestsList.size();
    }

    public void processCmBulkRequests(String userName){
        destinationUserName = userName;
        allRequestsList = getRequestList();
        CmBulkExportRequest exportRequest = allRequestsList.get(0);
        triggerFileGeneration(exportRequest);
    }

    public void populateWeeklyRequest() {
        allRequestsList.add(new HwFilterSupportUnitRequest());
        allRequestsList.add(new HwFilterEquipmentRequest());
        allRequestsList.add(new HwFilterInventoryWeeklyRequest());
        allRequestsList.add(new HwFilterBscRequest());
        allRequestsList.add(new HwFilterBspRequest());
        allRequestsList.add(new HwFilterRadioRequest());
        allRequestsList.add(new LicFilterFeatureKeyRequest());
        allRequestsList.add(new LicFilterInventoryCapacityKeyRequest());
        allRequestsList.add(new LicFilterInventoryElectronicKeyRequest());
    }

    private void triggerFileGeneration(CmBulkExportRequest request) {
        CmBulkExportRequest exportRequest = getJobName(request);
        HttpClient httpClient = null;
        int responseCode = 0;
        final String jsonString = new Gson().toJson(exportRequest);
        try {
            httpClient = httpClientUtility.buildHttpClient(IMPORT_EXPORT_SERVICE, IMPORT_EXPORT_PORT, false);
            final HttpRequestBuilder requestBuilder = httpClientUtility.requestBuilderForPost(IMPORT_EXPORT_SERVICE, IMPORT_EXPORT_PORT, BULK_EXPORT_URL, jsonString, getHeaders(), HttpScheme.HTTP.toString());
            response = httpClientUtility.sendHttpRequest(httpClient , requestBuilder);
            logger.debug("CM file trigger response: {}", response);
            if(response != null) {
                responseCode = response.getResponseCode();
                if(responseCode == 200) {
                    final JsonObject jsonObject = new Gson().fromJson(response.getResponseContentText(), JsonObject.class);
                    cmRequestStatusCache.setJobId(jsonObject.get(JOB_ID).getAsInt());
                    cmRequestStatusCache.setHttpError(Boolean.FALSE);
                }
            }
        }
        catch (PushServiceException e){
            logger.error("Http Error, while triggering CM Bulk Export.");
            logger.debug(e.getMessage(), e);
        }
        catch (final Exception e) {
            logger.error("Error while triggering CM Bulk Export.");
            logger.debug(e.getMessage(), e);
        }
        finally {
            if( responseCode != 200 && responseCode != 0){
                cmRequestStatusCache.setHttpError(Boolean.TRUE);
                allRequestsList.remove(0);
            }
            httpClientUtility.closeHttpClient(httpClient);
        }
    }

    private String getCmFileByJobId(){
        HttpClient httpClient = null;
        String jobStatus = null;
        int jobId = cmRequestStatusCache.getJobId();
        String url = BULK_EXPORT_URL.concat("/").concat(String.valueOf(jobId));
        try {
            httpClient = httpClientUtility.buildHttpClient(IMPORT_EXPORT_SERVICE, IMPORT_EXPORT_PORT, false);
            final HttpRequestBuilder requestBuilder = httpClientUtility.requestBuilderForGet(IMPORT_EXPORT_SERVICE, IMPORT_EXPORT_PORT, url, getHeaders(), HttpScheme.HTTP.toString());
            response = httpClientUtility.sendHttpRequest(httpClient , requestBuilder);
            if(response != null) {
                final JsonObject jsonObject = new Gson().fromJson(response.getResponseContentText(), JsonObject.class);
                if (response.getResponseCode() == 200) {
                    jobStatus = jsonObject.get(JOB_STATUS).getAsString();
                    logger.debug("For CM File JobId: {}, Job Status is {}", jobId, jobStatus);
                    if(jobStatus.equals(JOB_STATUS_COMPLETED)) {
                        String comma = ", ";
                        final StringBuilder sb = new StringBuilder();
                        sb.append(JOB_STATUS).append(DELIMITER).append(jsonObject.get(JOB_STATUS).getAsString());
                        sb.append(comma).append(STATUS_REASON).append(DELIMITER).append(jsonObject.get(STATUS_REASON).getAsString());
                        sb.append(comma).append(NODES_EXPORTED).append(DELIMITER).append(jsonObject.get(NODES_EXPORTED).getAsString());
                        sb.append(comma).append(NODES_NOT_EXPORTED).append(DELIMITER).append(jsonObject.get(NODES_NOT_EXPORTED).getAsString());
                        sb.append(comma).append(NODES_NO_MATCH_FOUND).append(DELIMITER).append(jsonObject.get(NODES_NO_MATCH_FOUND).getAsString());
                        sb.append(comma).append(NO_OF_MO_EXPORTED).append(DELIMITER).append(jsonObject.get(NO_OF_MO_EXPORTED).getAsString());
                        systemRecorder.recordEvent("CM Bulk file details ", EventLevel.DETAILED, sb.toString(),"","");
                    }
                }
            }
        }
        catch (PushServiceException e){
            logger.error("Error while verifying CM Bulk Export File generation. {}", e.getMessage());
            logger.debug(e.getMessage(), e);
        }
        catch (final Exception e) {
            logger.error("Error while verifying CM Bulk Export.");
            logger.debug("Error while checking CM Bulk File generation status", e);
        } finally {
            httpClientUtility.closeHttpClient(httpClient);
        }
        return jobStatus;
    }

    private CmBulkExportRequest getJobName(CmBulkExportRequest cmRequest){
        jobName = LocalDateTime.now().toString();
        jobName = jobName.replace(":", "");
        jobName = jobName.replace(".", "");
        jobName = jobName.replace(DELIMITER, "");
        jobName = jobName.concat("_").concat(destinationUserName).concat("_");

        if(cmRequest instanceof HwFilterSupportUnitRequest){
            jobName = jobName.concat(JOB_NAME_HW_FILTER_1);
        } else if(cmRequest instanceof HwFilterEquipmentRequest){
            jobName = jobName.concat(JOB_NAME_HW_FILTER_2);
        } else if (cmRequest instanceof HwFilterInventoryWeeklyRequest){
            jobName = jobName.concat(JOB_NAME_HW_FILTER_3);
        } else if(cmRequest instanceof HwFilterBscRequest){
            jobName = jobName.concat(JOB_NAME_HW_FILTER_4);
        } else if(cmRequest instanceof HwFilterBspRequest){
            jobName = jobName.concat(JOB_NAME_HW_FILTER_5);
        } else if(cmRequest instanceof HwFilterRadioRequest){
            jobName = jobName.concat(JOB_NAME_HW_FILTER_6);
        } else if (cmRequest instanceof LicFilterFeatureKeyRequest) {
            jobName = jobName.concat(JOB_NAME_LIC_FILTER_1);
        } else if (cmRequest instanceof LicFilterInventoryCapacityKeyRequest) {
            jobName = jobName.concat(JOB_NAME_LIC_FILTER_2);
        } else if (cmRequest instanceof LicFilterInventoryElectronicKeyRequest) {
            jobName = jobName.concat(JOB_NAME_LIC_FILTER_3);
        } else if (cmRequest instanceof SwFilterNetworkElementRequest){
            jobName = jobName.concat(JOB_NAME_SW_FILTER_1);
        } else if (cmRequest instanceof TcimNoFilterRequest){
            jobName = jobName.concat(JOB_NAME_TCIM_FILTER_1);
        } else {
            jobName = jobName.concat(JOB_NAME_BULK_FILTER_1);
        }
        cmRequest.setJobName(jobName);
        return cmRequest;
    }

    private Map<CharSequence, Object> getHeaders() {
        final Map<CharSequence, Object> headers = new HashMap<>();
        headers.put(USER_ID, ADMINISTRATOR);
        headers.put(CONTENT_TYPE, APPLICATION_JSON);
        return headers;
    }

    private List<CmBulkExportRequest> getRequestList(){
        List<CmBulkExportRequest> requestList = new ArrayList<>();

        requestList.add(new AllNodesNoFilterRequest());
        requestList.add(new TcimNoFilterRequest());
        requestList.add(new SwFilterNetworkElementRequest());

        return requestList;
    }

    public void triggerCmBulkFileTransfer() {
        if(cmRequestStatusCache.getHttpError().equals(Boolean.FALSE)) {
            transferCompletedCmBulkFiles();
        }
        else {
            logger.debug("No Job Id to verify, Checking the request list for triggering next CM file generation");
            cmRequestStatusCache.setHttpError(Boolean.FALSE);
            triggerNextFileGeneration();
        }
    }

    private void transferCompletedCmBulkFiles(){
        String jobStatus = getCmFileByJobId();
        if(jobStatus != null) {
            if (jobStatus.equals(JOB_STATUS_COMPLETED)) {
                transferCmFiles();
                allRequestsList.remove(0);
                triggerNextFileGeneration();
            }
            else if (jobStatus.equals(JOB_STATUS_FAILED)) {
                allRequestsList.remove(0);
                triggerNextFileGeneration();
            }
        }
    }

    private void triggerNextFileGeneration(){
        if(!allRequestsList.isEmpty()) {
            CmBulkExportRequest exportRequest = allRequestsList.get(0);
            triggerFileGeneration(exportRequest);
        }
    }

    public void transferCmFiles(){
        String filePath = SLASH.concat(FILE_PATH).concat(jobName).concat(FILE_FORMAT);
        List<String> files = new ArrayList<>();
        files.add(filePath);
        cmFileCollectionEvent.setFileNames(files);
        eventEventSender.send(cmFileCollectionEvent);
        logger.debug("Event sent for CM files: {}", cmFileCollectionEvent);
    }

}
