package com.bc.calvalus.portal.client;

import com.bc.calvalus.portal.shared.DtoProcessorDescriptor;
import com.google.gwt.cell.client.EditTextCell;
import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.SelectionCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DoubleBox;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.IntegerBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.ProvidesKey;
import com.google.gwt.view.client.SingleSelectionModel;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo view that lets users submit a new L2 production.
 *
 * @author Norman
 */
public class BinningParametersForm2 extends Composite {

    private ListDataProvider<Variable> variableProvider;
    private SingleSelectionModel<Variable> variableSelectionModel;

    interface TheUiBinder extends UiBinder<Widget, BinningParametersForm2> {
    }

    public static class Variable {
        static int lastId = 0;
        Integer id = ++lastId;
        String name = "";

        String aggregator = "AVG";
        Double fillValue = Double.NaN;
        Double weightCoeff = 1.0;
        String maskExpr = "";
    }

    private static TheUiBinder uiBinder = GWT.create(TheUiBinder.class);

    @UiField
    IntegerBox steppingPeriodLength;
    @UiField
    IntegerBox compositingPeriodLength;
    @UiField
    IntegerBox periodCount;

    @UiField
    DoubleBox resolution;
    @UiField
    IntegerBox superSampling;

    @UiField
    IntegerBox targetWidth;
    @UiField
    IntegerBox targetHeight;

    HasValue<Date> minDate;
    HasValue<Date> maxDate;

    @UiField(provided = true)
    CellTable<Variable> variableTable;
    @UiField
    Button addVariableButton;
    @UiField
    Button removeVariableButton;

    DtoProcessorDescriptor processorDescriptor;

    public BinningParametersForm2() {

        variableProvider = new ListDataProvider<Variable>();
        variableProvider.getList().add(new Variable());

        // Set a key provider that provides a unique key for each contact. If key is
        // used to identify contacts when fields (such as the name and address)
        // change.
        ProvidesKey<Variable> keyProvider = new ProvidesKey<Variable>() {
            @Override
            public Object getKey(Variable item) {
                return item.id;
            }
        };
        variableTable = new CellTable<Variable>(keyProvider);
        variableTable.setWidth("100%", true);
        variableTable.setVisibleRange(0, 3);

        // Add a selection model so we can select cells.
        variableSelectionModel = new SingleSelectionModel<Variable>(keyProvider);
        variableTable.setSelectionModel(variableSelectionModel);

        // Initialize the columns.
        initTableColumns();

        // Add the CellList to the adapter in the database.
        variableProvider.addDataDisplay(variableTable);

        initWidget(uiBinder.createAndBindUi(this));

        addVariableButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                // todo - initialise with variable default values from processor (nf)
                variableProvider.getList().add(new Variable());
                variableProvider.refresh();
            }
        });

        removeVariableButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                Variable selectedVariable = variableSelectionModel.getSelectedObject();
                if (selectedVariable != null) {
                    variableProvider.getList().remove(selectedVariable);
                    variableProvider.refresh();
                }
            }
        });

        /*
        inputVariables.addItem("chl_conc");
        inputVariables.addItem("tsm");
        inputVariables.addItem("Z90_max");
        inputVariables.addItem("chiSquare");
        inputVariables.addItem("turbidity_index");
        inputVariables.setSelectedIndex(0);
        maskExpr.setText("!l1_flags.INVALID AND !l1_flags.LAND_OCEAN");
        fillValue.setValue(Double.NaN);
        aggregator.addItem("Average", "AVG");
        aggregator.addItem("Max. Likelihood Average", "AVG_ML");
        aggregator.addItem("Minimum + Maximum", "MIN_MAX");
        aggregator.addItem("P90 Percentile", "PERCENTILE");
        aggregator.setSelectedIndex(1);
        weightCoeff.setValue(0.5);
        */

        steppingPeriodLength.setValue(10);
        steppingPeriodLength.addValueChangeHandler(new ValueChangeHandler<Integer>() {
            @Override
            public void onValueChange(ValueChangeEvent<Integer> event) {
                updatePeriodCount();
            }
        });

        compositingPeriodLength.setValue(10);

        periodCount.setValue(0);
        periodCount.setEnabled(false);

        superSampling.setValue(1);
        resolution.setValue(9.28);

        targetWidth.setValue(0);
        targetWidth.setEnabled(false);

        targetHeight.setValue(0);
        targetHeight.setEnabled(false);
    }

    public void setTimeRange(HasValue<Date> minDate, HasValue<Date> maxDate) {
        this.minDate = minDate;
        this.maxDate = maxDate;
        updatePeriodCount();
    }

    public void setSelectedProcessor(DtoProcessorDescriptor selectedProcessor) {
        // todo - update variable list from processor (nf)
        /*
        int selectedIndex = inputVariables.getSelectedIndex();
        String selectedItem = null;
        if (selectedIndex != -1) {
            selectedItem = inputVariables.getItemText(selectedIndex);
        }
        inputVariables.clear();
        DtoProcessorVariable[] processorVariables = selectedProcessor.getProcessorVariables();
        int newSelectedIndex = 0;
        int index = 0;
        for (DtoProcessorVariable processorVariable : processorVariables) {
            String processorVariableName = processorVariable.getName();
            inputVariables.addItem(processorVariableName);
            if (selectedIndex != -1 && processorVariableName.equals(selectedItem)) {
                newSelectedIndex = index;
            }
            index++;
        }
        inputVariables.setSelectedIndex(newSelectedIndex);
        */
    }

    private void updatePeriodCount() {
        long millisPerDay = 24L * 60L * 60L * 1000L;
        long deltaMillis = maxDate.getValue().getTime() - minDate.getValue().getTime();
        int deltaDays = (int) ((millisPerDay + deltaMillis) / millisPerDay);
        periodCount.setValue(deltaDays / steppingPeriodLength.getValue());
    }

    public void validateForm() throws ValidationException {
        boolean periodCountValid = periodCount.getValue() >= 1;
        if (!periodCountValid) {
            throw new ValidationException(periodCount, "Period count must be >= 1");
        }

        boolean periodLengthValid = steppingPeriodLength.getValue() >= 1;
        if (!periodLengthValid) {
            throw new ValidationException(steppingPeriodLength, "Period length must be >= 1");
        }

        boolean compositingPeriodLengthValid = compositingPeriodLength.getValue() >= 1 && compositingPeriodLength.getValue() <= steppingPeriodLength.getValue();
        if (!compositingPeriodLengthValid) {
            throw new ValidationException(compositingPeriodLength, "Compositing period length must be >= 1 and less or equal to than period");
        }
/*
        boolean weightCoeffValid = weightCoeff.getValue() >= 0.0 && weightCoeff.getValue() <= 1.0;
        if (!weightCoeffValid) {
            throw new ValidationException(weightCoeff, "Weight coefficient must be >= 0 and <= 1");
        }
*/
        boolean resolutionValid = resolution.getValue() > 0.0;
        if (!resolutionValid) {
            throw new ValidationException(resolution, "Resolution must greater than zero");
        }

        boolean superSamplingValid = superSampling.getValue() >= 1 && superSampling.getValue() <= 9;
        if (!superSamplingValid) {
            throw new ValidationException(superSampling, "Super-sampling must be >= 1 and <= 9");
        }

        // todo - validate variable list (nf)
    }

    public Map<String, String> getValueMap() {
        Map<String, String> parameters = new HashMap<String, String>();
        /*
        parameters.put("inputVariables", inputVariables.getValue(inputVariables.getSelectedIndex()));
        parameters.put("maskExpr", maskExpr.getText());
        parameters.put("fillValue", fillValue.getText());
        parameters.put("aggregator", aggregator.getValue(aggregator.getSelectedIndex()));
        parameters.put("weightCoeff", weightCoeff.getText());
        */
        List<Variable> variables = variableProvider.getList();
        int variablesLength = variables.size();
        parameters.put("variables.length", variables.size() + "");
        for (int i = 0; i < variablesLength; i++) {
            Variable variable = variables.get(i);
            String prefix = "variables." + i;
            parameters.put(prefix + ".name", variable.name);
            parameters.put(prefix + ".aggregator", variable.aggregator);
            parameters.put(prefix + ".weightCoeff", variable.weightCoeff + "");
            parameters.put(prefix + ".fillValue", variable.fillValue + "");
            parameters.put(prefix + ".maskExpr", variable.maskExpr);
        }
        parameters.put("periodLength", steppingPeriodLength.getText());
        parameters.put("compositingPeriodLength", compositingPeriodLength.getText());
        parameters.put("resolution", resolution.getText());
        parameters.put("superSampling", superSampling.getText());
        return parameters;
    }


    private void initTableColumns() {
        Column<Variable, String> nameColumn = createNameColumn();
        variableTable.addColumn(nameColumn, "Variable");
        variableTable.setColumnWidth(nameColumn, 14, Style.Unit.EM);

        Column<Variable, String> aggregatorColumn = createAggregatorColumn();
        variableTable.addColumn(aggregatorColumn, "Aggregator");
        variableTable.setColumnWidth(aggregatorColumn, 10, Style.Unit.EM);

        Column<Variable, String> weightCoeffColumn = createWeightCoeffColumn();
        variableTable.addColumn(weightCoeffColumn, "Weight");
        variableTable.setColumnWidth(weightCoeffColumn, 8, Style.Unit.EM);

        Column<Variable, String> fillValueColumn = createFillValueColumn();
        variableTable.addColumn(fillValueColumn, "Fill");
        variableTable.setColumnWidth(fillValueColumn, 8, Style.Unit.EM);

        Column<Variable, String> maskExprColumn = createValidMaskColumn();
        variableTable.addColumn(maskExprColumn, "Mask expression");
        variableTable.setColumnWidth(maskExprColumn, 24, Style.Unit.EM);
    }

    private Column<Variable, String> createNameColumn() {
        List<String> nameList = Arrays.asList(
                "chl_conc",
                "tsm",
                "Z90_max",
                "chiSquare",
                "turbidity_index"
        );

        SelectionCell variableNameCell = new SelectionCell(nameList);
        Column<Variable, String> nameColumn = new Column<Variable, String>(variableNameCell) {
            @Override
            public String getValue(Variable variable) {
                return variable.name;
            }
        };
        nameColumn.setFieldUpdater(new FieldUpdater<Variable, String>() {
            public void update(int index, Variable variable, String value) {
                variable.name = value;
                variableProvider.refresh();
            }
        });
        return nameColumn;
    }

    private Column<Variable, String> createAggregatorColumn() {

        List<String> valueList = Arrays.asList(
                "AVG",
                "AVG_ML",
                "MIN_MAX",
                "P90"
        );

        SelectionCell aggregatorCell = new SelectionCell(valueList);
        Column<Variable, String> aggregatorColumn = new Column<Variable, String>(aggregatorCell) {
            @Override
            public String getValue(Variable variable) {
                return variable.aggregator;
            }
        };
        aggregatorColumn.setFieldUpdater(new FieldUpdater<Variable, String>() {
            public void update(int index, Variable variable, String value) {
                variable.aggregator = value;
                variableProvider.refresh();
            }
        });
        return aggregatorColumn;
    }

    private Column<Variable, String> createWeightCoeffColumn() {
        Column<Variable, String> fillValueColumn = new Column<Variable, String>(new EditTextCell()) {
            @Override
            public String getValue(Variable object) {
                return object.weightCoeff + "";
            }
        };
        fillValueColumn.setFieldUpdater(new FieldUpdater<Variable, String>() {
            public void update(int index, Variable object, String value) {
                object.weightCoeff = Double.parseDouble(value);
                variableProvider.refresh();
            }
        });
        return fillValueColumn;
    }

    private Column<Variable, String> createFillValueColumn() {
        Column<Variable, String> fillValueColumn = new Column<Variable, String>(new EditTextCell()) {
            @Override
            public String getValue(Variable object) {
                return object.fillValue + "";
            }
        };
        fillValueColumn.setFieldUpdater(new FieldUpdater<Variable, String>() {
            public void update(int index, Variable object, String value) {
                object.fillValue = Double.parseDouble(value);
                variableProvider.refresh();
            }
        });
        return fillValueColumn;
    }

    private Column<Variable, String> createValidMaskColumn() {
        Column<Variable, String> validMaskColumn = new Column<Variable, String>(new EditTextCell()) {
            @Override
            public String getValue(Variable object) {
                return object.maskExpr;
            }
        };
        validMaskColumn.setFieldUpdater(new FieldUpdater<Variable, String>() {
            public void update(int index, Variable object, String value) {
                object.maskExpr = value;
                variableProvider.refresh();
            }
        });
        return validMaskColumn;
    }
}