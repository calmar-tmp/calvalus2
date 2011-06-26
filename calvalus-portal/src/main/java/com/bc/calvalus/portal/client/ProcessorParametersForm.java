package com.bc.calvalus.portal.client;

import com.bc.calvalus.portal.shared.GsProcessorDescriptor;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;

/**
 * Demo view that lets users submit a new L2 production.
 *
 * @author Norman
 */
public class ProcessorParametersForm extends Composite {
    public static final String HIDE_HELP_TEXT = "Hide description";
    public static final String SHOW_HELP_TEXT = "Show description";
    public static final String UPLOAD_ACTION_URL = GWT.getModuleBaseURL() + "upload";

    private GsProcessorDescriptor processorDescriptor;

    interface TheUiBinder extends UiBinder<Widget, ProcessorParametersForm> {
    }

    private static TheUiBinder uiBinder = GWT.create(TheUiBinder.class);

    @UiField
    HTML title;
    @UiField
    TextArea processorParametersArea;
    @UiField
    HTML processorDescriptionHTML;
    @UiField
    FileUpload fileUpload;
    @UiField
    FormPanel uploadForm;
    @UiField
    Anchor showHelpAnchor;

    boolean helpVisible;


    public ProcessorParametersForm(String title) {
        initWidget(uiBinder.createAndBindUi(this));

        this.title.setHTML(title);

        fileUpload.addChangeHandler(new FileUploadChangeHandler());

        uploadForm.addSubmitHandler(new FormPanel.SubmitHandler() {
            public void onSubmit(FormPanel.SubmitEvent event) {
                // todo - check inputs
            }
        });
        uploadForm.addSubmitCompleteHandler(new FormPanel.SubmitCompleteHandler() {
            public void onSubmitComplete(FormPanel.SubmitCompleteEvent event) {
                String results = event.getResults();
                processorParametersArea.setText(results != null ? results : "");
            }
        });

        showHelpAnchor.setText(HIDE_HELP_TEXT);
        helpVisible = true;

        showHelpAnchor.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                helpVisible = !helpVisible;
                if (helpVisible) {
                    showHelpAnchor.setText(HIDE_HELP_TEXT);
                    processorDescriptionHTML.setHTML(processorDescriptor.getDescriptionHtml());
                    processorParametersArea.setCharacterWidth(60);
                } else {
                    showHelpAnchor.setText(SHOW_HELP_TEXT);
                    processorDescriptionHTML.setHTML("");
                    processorParametersArea.setCharacterWidth(100);
                }
            }
        });
    }

    public String getProcessorParameters() {
        return processorParametersArea.getValue();
    }

    public void setProcessorDescriptor(GsProcessorDescriptor processorDescriptor) {
        this.processorDescriptor = processorDescriptor;
        if (processorDescriptor != null) {
            processorParametersArea.setValue(processorDescriptor.getDefaultParameter());
            processorDescriptionHTML.setHTML(processorDescriptor.getDescriptionHtml());
        } else {
            processorParametersArea.setValue("");
            processorDescriptionHTML.setHTML("");
        }
    }

    public void validateForm() throws ValidationException {
    }

    private class FileUploadChangeHandler implements ChangeHandler {

        public void onChange(ChangeEvent event) {
            String filename = fileUpload.getFilename();
            if (filename != null && !filename.isEmpty()) {
                // Because we're going to add a FileUpload widget, we'll need to set the
                // form to use the POST method, and multi-part MIME encoding.
                uploadForm.setAction(UPLOAD_ACTION_URL + "?echo=1");
                uploadForm.setEncoding(FormPanel.ENCODING_MULTIPART);
                uploadForm.setMethod(FormPanel.METHOD_POST);
                uploadForm.submit();
            }
        }
    }
}