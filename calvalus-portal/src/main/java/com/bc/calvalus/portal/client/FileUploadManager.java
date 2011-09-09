package com.bc.calvalus.portal.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.FormPanel;

/**
 * Enables file uploads.
 *
 * @author Norman
 */
public class FileUploadManager {


    public static void submitOnChange(final FormPanel uploadForm,
                                      final FileUpload fileUpload,
                                      final String parameters,
                                      final FormPanel.SubmitHandler submitHandler,
                                      final FormPanel.SubmitCompleteHandler submitCompleteHandler) {
        configureForm(uploadForm, parameters, submitHandler, submitCompleteHandler);
        fileUpload.addChangeHandler(new ChangeHandler() {
            @Override
            public void onChange(ChangeEvent event) {
                String filename = fileUpload.getFilename();
                if (filename != null && !filename.isEmpty()) {
                    uploadForm.submit();
                }
            }
        });
    }


    public static void configureForm(final FormPanel uploadForm,
                                     final String parameters,
                                     final FormPanel.SubmitHandler submitHandler,
                                     final FormPanel.SubmitCompleteHandler submitCompleteHandler) {
        // Because we're going to add a FileUpload widget, we'll need to set the
        // form to use the POST method, and multi-part MIME encoding.
        String actionUrl = GWT.getModuleBaseURL() + "upload" + (!parameters.isEmpty() ? "?" + parameters : "");
        GWT.log("Action URL: " + actionUrl);
        uploadForm.setAction(actionUrl);
        uploadForm.setEncoding(FormPanel.ENCODING_MULTIPART);
        uploadForm.setMethod(FormPanel.METHOD_POST);
        uploadForm.addSubmitHandler(submitHandler);
        uploadForm.addSubmitCompleteHandler(submitCompleteHandler);
    }

}
