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
package com.ericsson.oss.services.fnt.impl

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.rule.SpyImplementation
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.commonlibrary.httpclient.common.HttpRequestListener
import com.ericsson.oss.services.fnt.Exception.PushServiceException
import com.ericsson.oss.services.fnt.cmrequests.CmBulkExportRequest
import com.ericsson.oss.services.fnt.cmrequests.weeklyrequests.HwFilterBscRequest
import com.ericsson.oss.services.fnt.cmrequests.weeklyrequests.HwFilterEquipmentRequest
import com.ericsson.oss.services.fnt.cmrequests.weeklyrequests.LicFilterFeatureKeyRequest
import com.ericsson.oss.services.fnt.cmrequests.weeklyrequests.LicFilterInventoryCapacityKeyRequest
import com.ericsson.oss.services.fnt.cmrequests.weeklyrequests.LicFilterInventoryElectronicKeyRequest
import com.ericsson.oss.services.fnt.cmrequests.dailyrequests.SwFilterNetworkElementRequest
import com.ericsson.oss.services.fnt.cmrequests.weeklyrequests.HwFilterRadioRequest
import com.ericsson.oss.services.fnt.cmrequests.weeklyrequests.HwFilterInventoryWeeklyRequest
import com.ericsson.oss.services.fnt.cmrequests.weeklyrequests.HwFilterBspRequest
import com.ericsson.oss.services.fnt.utils.HttpClientUtility

import javax.inject.Inject

class TriggerCmBulkExportSpec extends CdiSpecification{

    @ObjectUnderTest
    TriggerCmBulkExport triggerCMBulkExport

    @SpyImplementation
    HttpClientUtility httpClientUtility

    @MockedImplementation
    HttpRequestListener httpRequestListener

    @Inject
    CmRequestStatusCache requestStatusCache

    def setup() {
        triggerCMBulkExport.destinationUserName = "testuser"
    }

    def 'verify if we are getting proper jobName for CM #jobNameSuffix request'(){
        given:
        CmBulkExportRequest request = requestType
        when:
        CmBulkExportRequest request1 = triggerCMBulkExport.getJobName(request)
        then:
        request1.getJobName().contains("testuser_" + jobNameSuffix)
        where:
        requestType | jobNameSuffix
        new LicFilterFeatureKeyRequest()  |  "LIC1"
        new LicFilterInventoryCapacityKeyRequest()  | "LIC2"
        new LicFilterInventoryElectronicKeyRequest() | "LIC3"
        new HwFilterEquipmentRequest()  | "HW2"
        new HwFilterBscRequest()  | "HW4"
        new HwFilterInventoryWeeklyRequest()  | "HW3"
        new HwFilterBspRequest()  | "HW5"
        new HwFilterRadioRequest() | "HW6"
    }

    def 'verify the trigger for Bulk CM file generation'(){
        given:
        triggerCMBulkExport.response = Mock(HttpRequestListener)
        triggerCMBulkExport.response.getResponseCode() >> { return 200 }
        triggerCMBulkExport.response.responseContentText >> '{"_links":{"self":{"href":"/bulk/export/jobs"},"status":{"href":"/bulk/export/jobs/703206"}},"id":703206,"status":"STARTING","type":"EXPORT"}'
        httpClientUtility.sendHttpRequest(*_) >> { return triggerCMBulkExport.response }
        when: 'we get 200 status code'
        triggerCMBulkExport.processCmBulkRequests("testuser")
        then: 'we should have the jobId in cache'
        noExceptionThrown()
        requestStatusCache.getJobId() == 703206
    }

    def 'verify if we get null response while triggering file generation'(){
        given:
        triggerCMBulkExport.response = null
        httpClientUtility.sendHttpRequest(*_) >> { return triggerCMBulkExport.response }
        when:
        triggerCMBulkExport.processCmBulkRequests("testuser")
        then:
        noExceptionThrown()
    }

    def 'verify triggerFileGen if status code is other than 200'(){
        given:
        triggerCMBulkExport.response = Mock(HttpRequestListener)
        triggerCMBulkExport.response.getResponseCode() >> { return 404 }
        triggerCMBulkExport.response.responseContentText >> '{"_links":{"self":{"href":"/bulk/export/jobs"},"status":{"href":"/bulk/export/jobs/703206"}},"id":703206,"status":"STARTING","type":"EXPORT"}'
        httpClientUtility.sendHttpRequest(*_) >> { return triggerCMBulkExport.response }
        when:
        triggerCMBulkExport.processCmBulkRequests("testuser")
        then: 'we will not get any jobId for this post call and post call error status should set to true'
        noExceptionThrown()
        requestStatusCache.getHttpError() == Boolean.TRUE
    }

    def 'verify triggerFileGen for PushServiceException'(){
        given:
        CmBulkExportRequest request = new SwFilterNetworkElementRequest()
        httpClientUtility.sendHttpRequest(*_) >> { throw new PushServiceException("Exception during Post call")}
        when:
        triggerCMBulkExport.triggerFileGeneration(request)
        then:
        noExceptionThrown()
    }

    def 'verify triggerFileGen for Exception'(){
        given:
        CmBulkExportRequest request = new HwFilterRadioRequest()
        httpClientUtility.sendHttpRequest(*_) >> { throw new NullPointerException()}
        when:
        triggerCMBulkExport.triggerFileGeneration(request)
        then:
        noExceptionThrown()
    }

    def 'verify populateWeeklyRequest'(){
        when:
        triggerCMBulkExport.populateWeeklyRequest()
        then:
        triggerCMBulkExport.allRequestsList.size() == 9
    }

    def 'verify getHeaders'(){
        when:
        Map<CharSequence, Object> headers = triggerCMBulkExport.getHeaders()
        then:
        headers.containsKey("X-Tor-UserID") && headers.containsValue("administrator")
    }

    def 'verify CM file generation for a jobId'(){
        given:
        triggerCMBulkExport.getRequestList()
        requestStatusCache.setJobId(703206)
        triggerCMBulkExport.response = Mock(HttpRequestListener)
        triggerCMBulkExport.response.getResponseCode() >> { return 200 }
        triggerCMBulkExport.response.responseContentText >> '{"_links":{"self":{"href":"/bulk/export/jobs/692531"},"reports":{"href":"/bulk/export/reports/692531"}},"id":692531,"status":"COMPLETED","type":"EXPORT","creationTime":"20210617T064923765","startTime":"20210617T064923769","endTime":"20210617T081457090","lastUpdateTime":"20210617T064923769","jobName":"bulk_batch1","expectedNodesExported":25976,"nodesExported":25956,"nodesNotExported":20,"nodesNoMatchFound":0,"numberOfMosExported":109503362,"userId":"testuser","fileUri":"/bulk/export/PZcVUIcVUHDAbulk_batch1.zip"}'
        httpClientUtility.sendHttpRequest(*_) >> { return triggerCMBulkExport.response }
        when:
        triggerCMBulkExport.getCmFileByJobId()
        then:
        noExceptionThrown()
    }

    def 'verify CM file generation for a null response'(){
        given:
        triggerCMBulkExport.getRequestList()
        requestStatusCache.setJobId(703206)
        triggerCMBulkExport.response = null
        httpClientUtility.sendHttpRequest(*_) >> { return triggerCMBulkExport.response }
        when:
        triggerCMBulkExport.getCmFileByJobId()
        then:
        noExceptionThrown()
    }

    def 'verify CM file generation for a not 200 response'(){
        given:
        triggerCMBulkExport.getRequestList()
        requestStatusCache.setJobId(703206)
        triggerCMBulkExport.response = Mock(HttpRequestListener)
        triggerCMBulkExport.response.getResponseCode() >> { return 404 }
        triggerCMBulkExport.response.responseContentText >> '{"_links":{"self":{"href":"/bulk/export/jobs/692531"},"reports":{"href":"/bulk/export/reports/692531"}},"id":692531,"status":"COMPLETED","type":"EXPORT","creationTime":"20210617T064923765","startTime":"20210617T064923769","endTime":"20210617T081457090","lastUpdateTime":"20210617T064923769","jobName":"bulk_batch1","expectedNodesExported":25976,"nodesExported":25956,"nodesNotExported":20,"nodesNoMatchFound":0,"numberOfMosExported":109503362,"userId":"testuser","fileUri":"/bulk/export/PZcVUIcVUHDAbulk_batch1.zip"}'
        httpClientUtility.sendHttpRequest(*_) >> { return triggerCMBulkExport.response }
        when:
        triggerCMBulkExport.getCmFileByJobId()
        then:
        noExceptionThrown()
    }

    def 'verify next cm file generation will trigger if previous one failed in case'(){
        given:
        triggerCMBulkExport.allRequestsList = triggerCMBulkExport.getRequestList()
        requestStatusCache.setHttpError(Boolean.TRUE)
        when:
        triggerCMBulkExport.triggerCmBulkFileTransfer()
        then: 'next post call will trigger'
        requestStatusCache.getHttpError() == Boolean.FALSE
        noExceptionThrown()
    }

    def 'verify cm file transfer processing for a jobId'(){
        given:
        triggerCMBulkExport.allRequestsList = triggerCMBulkExport.getRequestList()
        triggerCMBulkExport.jobName = "20210719T105807055_testuser_LIC"
        requestStatusCache.setJobId(703206)
        requestStatusCache.setHttpError(Boolean.FALSE)
        triggerCMBulkExport.response = Mock(HttpRequestListener)
        triggerCMBulkExport.response.getResponseCode() >> { return 200 }
        triggerCMBulkExport.response.responseContentText >> '{"_links":{"self":{"href":"/bulk/export/jobs/692531"},"reports":{"href":"/bulk/export/reports/692531"}},"id":692531,"status":"COMPLETED","type":"EXPORT","creationTime":"20210617T064923765","startTime":"20210617T064923769","endTime":"20210617T081457090","lastUpdateTime":"20210617T064923769","jobName":"bulk_batch1","expectedNodesExported":25976,"nodesExported":25956,"nodesNotExported":20,"nodesNoMatchFound":0,"numberOfMosExported":109503362,"userId":"testuser","fileUri":"/bulk/export/PZcVUIcVUHDAbulk_batch1.zip"}'
        httpClientUtility.sendHttpRequest(*_) >> { return triggerCMBulkExport.response }
        when:
        triggerCMBulkExport.triggerCmBulkFileTransfer()
        then: 'next post call will trigger'
        noExceptionThrown()
    }

    def 'verify if the status for get call of particular jobId is FAILED'(){
        triggerCMBulkExport.allRequestsList = triggerCMBulkExport.getRequestList()
        requestStatusCache.setJobId(703206)
        requestStatusCache.setHttpError(Boolean.FALSE)
        triggerCMBulkExport.response = Mock(HttpRequestListener)
        triggerCMBulkExport.response.getResponseCode() >> { return 200 }
        triggerCMBulkExport.response.responseContentText >> '{"_links":{"self":{"href":"/bulk/export/jobs/692531"},"reports":{"href":"/bulk/export/reports/692531"}},"id":692531,"status":"FAILED","type":"EXPORT","creationTime":"20210617T064923765","startTime":"20210617T064923769","endTime":"20210617T081457090","lastUpdateTime":"20210617T064923769","jobName":"bulk_batch1","expectedNodesExported":25976,"nodesExported":25956,"nodesNotExported":20,"nodesNoMatchFound":0,"numberOfMosExported":109503362,"userId":"testuser","fileUri":"/bulk/export/PZcVUIcVUHDAbulk_batch1.zip"}'
        httpClientUtility.sendHttpRequest(*_) >> { return triggerCMBulkExport.response }
        when:
        triggerCMBulkExport.transferCompletedCmBulkFiles()
        then: 'next post call will trigger'
        noExceptionThrown()
    }

    def 'verify triggerNextFileGeneration if request list is empty'(){
        when:
        triggerCMBulkExport.triggerNextFileGeneration()
        then: 'no post call will trigger'
        noExceptionThrown()
    }

    def 'verify if we get PushServiceException while getting jobstatus'(){
        given:
        requestStatusCache.setHttpError(Boolean.FALSE)
        httpClientUtility.sendHttpRequest(*_) >> { throw new PushServiceException("Exception during Get call")}
        when:
        triggerCMBulkExport.transferCompletedCmBulkFiles()
        then: 'Exception should be handled'
        noExceptionThrown()
    }

    def 'verify if getCmFileByJobId for Exception'(){
        given:
        httpClientUtility.sendHttpRequest(*_) >> { throw new NullPointerException()}
        when:
        triggerCMBulkExport.getCmFileByJobId()
        then: 'Exception should be handled'
        noExceptionThrown()
    }

    def 'Transfer CM files to queue'(){
        given:
        CmBulkExportRequest request = new HwFilterInventoryWeeklyRequest()
        CmBulkExportRequest request1 = triggerCMBulkExport.getJobName(request)
        triggerCMBulkExport.jobName >> request1.getJobName()
        when:
        triggerCMBulkExport.transferCmFiles()
        then: 'files should be transferred'
        noExceptionThrown()
    }

    def 'Check list size'(){
        given:
        triggerCMBulkExport.getRequestsListSize() >> 1
        when:
        triggerCMBulkExport.getRequestsListSize()
        then:
        noExceptionThrown()
    }

}
