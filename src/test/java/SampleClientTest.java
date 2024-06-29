import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;

public class SampleClientTest {
    Bundle response = null;
    IParser parser = null;
    FhirContext fhirContext = null;
    IGenericClient client = null;
    SampleClient.CustomInterceptor customInterceptor = null;

    @Before
    public void before() {
        fhirContext = FhirContext.forR4();
        client = fhirContext.newRestfulGenericClient("http://hapi.fhir.org/baseR4");
        parser = fhirContext.newJsonParser();
        customInterceptor = new SampleClient.CustomInterceptor();
    }

    @Test
    public void test_fetchPatientLastNameSmith() {
        response = client
                .search()
                .forResource("Patient")
                .where(Patient.FAMILY.matches().value("SMITH"))
                .returnBundle(Bundle.class)
                .execute();
        SampleClient.fetchPatientOrderedByPatientFirstName(response, parser);
        Assert.assertEquals(SampleClient.patientNames.size(), 20);
    }

    @Test
    public void test_fetchPatientsFromList() throws FileNotFoundException {
        SampleClient.fhirCalls(fhirContext, client, customInterceptor, false, 1);
        long averageTime = SampleClient.recordIterationTimeMap.get(1);
        Assert.assertTrue("Average times are greater than 100 ms.", averageTime > 100);

        List<String> names = new ArrayList<>();
        try (Scanner scanner = new Scanner(new File("src/main/resources/lastnames.txt"))) {
            while (scanner.hasNext()) {
                names.add(scanner.next());
            }
        }
        Set<String> keys = SampleClient.patientNamesWithRespTimeMap.keySet();
        Assert.assertTrue("Names from List from fetched response.", names.containsAll(keys));
    }
    
    @Test
    public void test_shorterAverageResponseTime() throws FileNotFoundException, InterruptedException {
        SampleClient.init();
        Assert.assertTrue(SampleClient.recordIterationTimeMap.get(1) > SampleClient.recordIterationTimeMap.get(2));
        Assert.assertTrue(SampleClient.recordIterationTimeMap.get(3) > SampleClient.recordIterationTimeMap.get(2));
    }

}
