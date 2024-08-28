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

import javax.ejb.Singleton;

@Singleton
public class CmRequestStatusCache {

    private int jobId;
    private Boolean httpError = false;

    public Boolean getHttpError() {
        return httpError;
    }

    public void setHttpError(Boolean httpError) {
        this.httpError = httpError;
    }

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

}
