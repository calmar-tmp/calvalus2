package com.bc.calvalus.wps.wpsoperations;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

import com.bc.calvalus.commons.ProcessState;
import com.bc.calvalus.production.Production;
import com.bc.calvalus.production.ProductionRequest;
import com.bc.calvalus.wps.calvalusfacade.CalvalusDataInputs;
import com.bc.calvalus.wps.calvalusfacade.CalvalusFacade;
import com.bc.calvalus.wps.localprocess.LocalProductionStatus;
import com.bc.calvalus.wps.utils.CalvalusExecuteResponseConverter;
import com.bc.calvalus.wps.utils.ExecuteRequestExtractor;
import com.bc.calvalus.wps.utils.ProcessorNameConverter;
import com.bc.wps.api.WpsRequestContext;
import com.bc.wps.api.WpsServerContext;
import com.bc.wps.api.schema.Execute;
import com.bc.wps.api.schema.ExecuteResponse;
import com.bc.wps.api.schema.ResponseDocumentType;
import com.bc.wps.api.schema.ResponseFormType;
import com.bc.wps.api.utils.WpsTypeConverter;
import com.bc.wps.utilities.PropertiesWrapper;
import org.junit.*;
import org.junit.rules.*;
import org.junit.runner.*;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author hans
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({
            CalvalusExecuteOperation.class, CalvalusFacade.class, ExecuteRequestExtractor.class,
            ProcessorNameConverter.class, ProductionRequest.class, CalvalusDataInputs.class,
            CalvalusExecuteResponseConverter.class
})
public class CalvalusExecuteOperationTest {

    private static final String MOCK_PROCESSOR_ID = "processor-00";
    private CalvalusExecuteOperation executeOperation;

    private Execute mockExecuteRequest;
    private WpsRequestContext mockRequestContext;

    @Rule
    public ExpectedException thrownException = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        PropertiesWrapper.loadConfigFile("calvalus-wps-test.properties");
        mockExecuteRequest = mock(Execute.class);
        mockRequestContext = mock(WpsRequestContext.class);
        WpsServerContext mockServerContext = mock(WpsServerContext.class);
        when(mockServerContext.getHostAddress()).thenReturn("dummyHostName");
        when(mockServerContext.getRequestUrl()).thenReturn("http://dummyUrl.com/bc-wps/wps/provider1");
        when(mockServerContext.getPort()).thenReturn(8000);
        when(mockRequestContext.getServerContext()).thenReturn(mockServerContext);
    }

    @Test
    public void canExecuteAsync() throws Exception {
        ResponseFormType mockResponseForm = mock(ResponseFormType.class);
        ResponseDocumentType mockResponseDocument = mock(ResponseDocumentType.class);
        when(mockResponseDocument.isStatus()).thenReturn(true);
        when(mockResponseDocument.isLineage()).thenReturn(false);
        when(mockResponseForm.getResponseDocument()).thenReturn(mockResponseDocument);
        when(mockExecuteRequest.getResponseForm()).thenReturn(mockResponseForm);
        when(mockExecuteRequest.getIdentifier()).thenReturn(WpsTypeConverter.str2CodeType("process1"));
        configureProcessingMock();

        CalvalusFacade mockCalvalusFacade = mock(CalvalusFacade.class);
        Production mockProduction = mock(Production.class);
        LocalProductionStatus mockStatus = mock(LocalProductionStatus.class);
        when(mockStatus.getJobId()).thenReturn("process-00");
        when(mockProduction.getId()).thenReturn("process-00");
        when(mockCalvalusFacade.orderProductionAsynchronous(any(Execute.class))).thenReturn(mockStatus);
        PowerMockito.whenNew(CalvalusFacade.class).withArguments(any(WpsRequestContext.class)).thenReturn(mockCalvalusFacade);

        executeOperation = new CalvalusExecuteOperation(MOCK_PROCESSOR_ID, mockRequestContext);
        ExecuteResponse executeResponse = executeOperation.execute(mockExecuteRequest);

        assertThat(executeResponse.getStatus().getProcessAccepted(),
                   equalTo("The request has been accepted. The status of the process can be found in the URL."));
        assertThat(executeResponse.getStatusLocation(), equalTo("http://dummyUrl.com/bc-wps/wps/provider1?Service=WPS&Request=GetStatus&JobId=process-00"));
        assertThat(executeResponse.getProcess().getIdentifier().getValue(), equalTo("process1"));
    }

    @Test
    public void canExecuteSync() throws Exception {
        ResponseFormType mockResponseForm = mock(ResponseFormType.class);
        ResponseDocumentType mockResponseDocument = mock(ResponseDocumentType.class);
        when(mockResponseDocument.isStatus()).thenReturn(false);
        when(mockResponseDocument.isLineage()).thenReturn(false);
        when(mockResponseForm.getResponseDocument()).thenReturn(mockResponseDocument);
        when(mockExecuteRequest.getResponseForm()).thenReturn(mockResponseForm);
        when(mockExecuteRequest.getIdentifier()).thenReturn(WpsTypeConverter.str2CodeType("process1"));
        configureProcessingMock();

        CalvalusFacade mockCalvalusFacade = mock(CalvalusFacade.class);
        Production mockProduction = mock(Production.class);
        List<String> resultUrlList = new ArrayList<>();
        resultUrlList.add("resultUrl1");
        resultUrlList.add("resultUrl2");
        LocalProductionStatus mockStatus = mock(LocalProductionStatus.class);
        when(mockStatus.getJobId()).thenReturn("process-00");
        when(mockStatus.getState()).thenReturn(ProcessState.COMPLETED.toString());
        when(mockStatus.getStopTime()).thenReturn(new Date(1451606400000L));
        when(mockStatus.getResultUrls()).thenReturn(resultUrlList);
        when(mockProduction.getId()).thenReturn("process-00");
        when(mockCalvalusFacade.orderProductionSynchronous(any(Execute.class))).thenReturn(mockStatus);
        when(mockCalvalusFacade.getProduction(anyString())).thenReturn(mockProduction);
        PowerMockito.whenNew(CalvalusFacade.class).withArguments(any(WpsRequestContext.class)).thenReturn(mockCalvalusFacade);

        executeOperation = new CalvalusExecuteOperation(MOCK_PROCESSOR_ID, mockRequestContext);
        ExecuteResponse executeResponse = executeOperation.execute(mockExecuteRequest);

        assertThat(executeResponse.getStatus().getProcessSucceeded(), equalTo("The request has been processed successfully."));
        assertThat(executeResponse.getProcess().getIdentifier().getValue(), equalTo("process1"));
        assertThat(executeResponse.getStatus().getCreationTime().toString(), equalTo("2016-01-01T01:00:00.000+01:00"));
        assertThat(executeResponse.getProcessOutputs().getOutput().size(), equalTo(2));
        assertThat(executeResponse.getProcessOutputs().getOutput().get(0).getReference().getHref(), equalTo("resultUrl1"));
        assertThat(executeResponse.getProcessOutputs().getOutput().get(1).getReference().getHref(), equalTo("resultUrl2"));
    }

    private ProductionRequest configureProcessingMock() throws Exception {
        ExecuteRequestExtractor mockRequestExtractor = mock(ExecuteRequestExtractor.class);
        PowerMockito.whenNew(ExecuteRequestExtractor.class).withArguments(any(Execute.class)).thenReturn(mockRequestExtractor);
        ProcessorNameConverter mockParser = mock(ProcessorNameConverter.class);
        PowerMockito.whenNew(ProcessorNameConverter.class).withArguments(anyString()).thenReturn(mockParser);
        CalvalusDataInputs mockCalvalusDataInputs = mock(CalvalusDataInputs.class);
        PowerMockito.whenNew(CalvalusDataInputs.class).withAnyArguments().thenReturn(mockCalvalusDataInputs);
        ProductionRequest mockProductionRequest = mock(ProductionRequest.class);
        PowerMockito.whenNew(ProductionRequest.class).withAnyArguments().thenReturn(mockProductionRequest);
        return mockProductionRequest;
    }

}