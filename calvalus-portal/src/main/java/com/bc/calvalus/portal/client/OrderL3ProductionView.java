package com.bc.calvalus.portal.client;

import com.bc.calvalus.portal.shared.GsProcessorDescriptor;
import com.bc.calvalus.portal.shared.GsProductionRequest;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashMap;
import java.util.Map;

/**
 * Demo view that lets users submit a new L3 production.
 *
 * @author Norman
 */
public class OrderL3ProductionView extends OrderProductionView {
    public static final String ID = OrderL3ProductionView.class.getName();

    private ProductSetSelectionForm productSetSelectionForm;
    private ProcessorSelectionForm processorSelectionForm;
    private ProductFilterForm productSetFilterForm;
    private ProcessorParametersForm processorParametersForm;
    private BinningParametersForm binningParametersForm;
    private OutputParametersForm outputParametersForm;
    private Widget widget;

    public OrderL3ProductionView(PortalContext portalContext) {
        super(portalContext);

        productSetSelectionForm = new ProductSetSelectionForm(getPortal().getProductSets());
        productSetFilterForm = new ProductFilterForm(portalContext.getRegions(), new ProductFilterForm.ChangeHandler() {
            @Override
            public void dateChanged(Map<String, String> data) {
                binningParametersForm.setTimeRange(productSetFilterForm.getMinDate(),
                                              productSetFilterForm.getMaxDate());
            }

            @Override
            public void regionChanged(Map<String, String> data) {
                // todo: l3ParametersForm.setRegion()
            }
        });
        processorSelectionForm = new ProcessorSelectionForm(portalContext.getProcessors(), "Processor");
        processorParametersForm = new ProcessorParametersForm("Processing Parameters");

        processorSelectionForm.addProcessorChangedHandler(new ProcessorSelectionForm.ProcessorChangedHandler() {
            @Override
            public void onProcessorChanged(GsProcessorDescriptor processorDescriptor) {
                processorParametersForm.setProcessorDescriptor(processorDescriptor);
                binningParametersForm.setSelectedProcessor(processorDescriptor);
            }
        });
        processorParametersForm.setProcessorDescriptor(processorSelectionForm.getSelectedProcessor());

        binningParametersForm = new BinningParametersForm();
        binningParametersForm.setSelectedProcessor(processorSelectionForm.getSelectedProcessor());

        outputParametersForm = new OutputParametersForm();

        HorizontalPanel orderPanel = new HorizontalPanel();
        orderPanel.setWidth("100%");
        orderPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
        orderPanel.add(new Button("Order Production", new ClickHandler() {
            public void onClick(ClickEvent event) {
                orderProduction();
            }
        }));

        HorizontalPanel panel1 = new HorizontalPanel();
        panel1.setSpacing(16);
        panel1.add(productSetSelectionForm);
        panel1.add(processorSelectionForm);

        VerticalPanel panel = new VerticalPanel();
        panel.setWidth("100%");
        panel.add(panel1);
        panel.add(productSetFilterForm);
        panel.add(processorParametersForm);
        panel.add(binningParametersForm);
        panel.add(outputParametersForm);
        panel.add(new HTML("<br/>"));
        panel.add(orderPanel);

        this.widget = panel;
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getViewId() {
        return ID;
    }

    @Override
    public String getTitle() {
        return "L3 Processing";
    }

    @Override
    protected boolean validateForm() {
        try {
            productSetSelectionForm.validateForm();
            processorSelectionForm.validateForm();
            binningParametersForm.validateForm();
            outputParametersForm.validateForm();
            return true;
        } catch (ValidationException e) {
            e.handle();
            return false;
        }
    }

    @Override
    protected GsProductionRequest getProductionRequest() {
        return new GsProductionRequest("L3", getProductionParameters());
    }

    // todo - Provide JUnit test for this method
    public HashMap<String, String> getProductionParameters() {
        GsProcessorDescriptor selectedProcessor = processorSelectionForm.getSelectedProcessor();
        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put("inputProductSetId", productSetSelectionForm.getInputProductSetId());
        parameters.put("outputFormat", outputParametersForm.getOutputFormat());
        parameters.put("autoStaging", outputParametersForm.isAutoStaging() + "");
        parameters.put("processorBundleName", selectedProcessor.getBundleName());
        parameters.put("processorBundleVersion", selectedProcessor.getBundleVersion());
        parameters.put("processorName", selectedProcessor.getExecutableName());
        parameters.put("processorParameters", processorParametersForm.getProcessorParameters());
        parameters.putAll(binningParametersForm.getValueMap());
        parameters.putAll(productSetFilterForm.getValueMap());
        return parameters;
    }
}