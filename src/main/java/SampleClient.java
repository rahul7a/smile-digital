import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.CacheControlDirective;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IHttpResponse;
import ca.uhn.fhir.util.StopWatch;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;

public class SampleClient {

    public static List<String> patientNames = new ArrayList<>();
    public static Map<String, Long> patientNamesWithRespTimeMap = new HashMap<>();
    public static Map<Integer, Long> recordIterationTimeMap = new HashMap<>();

    public static void main(String[] theArgs) throws FileNotFoundException, InterruptedException {

        init();

    }

    public static void init() throws FileNotFoundException, InterruptedException {

        FhirContext fhirContext = FhirContext.forR4();
        IGenericClient client = fhirContext.newRestfulGenericClient("http://hapi.fhir.org/baseR4");
        CustomInterceptor customInterceptor = new CustomInterceptor();

        for (int i = 1; i <= 3; i++) {
            fhirCalls(fhirContext, client, customInterceptor, false, i);
            Thread.sleep(5000);
        }

        recordIterationTimeMap.forEach((key, value) -> {
            System.out.println("iteration " + key + ": " + value + " ms.");
        });
    }

    public static void fhirCalls(FhirContext fhirContext, IGenericClient client, CustomInterceptor customInterceptor,
            boolean toDisplay, int iteration) throws FileNotFoundException {
        // Read from file
        List<String> names = new ArrayList<>();
        try (Scanner scanner = new Scanner(new File("src/main/resources/lastnames.txt"))) {
            while (scanner.hasNext()) {
                names.add(scanner.next());
            }
        }

        // Adding custom interceptor
        client.registerInterceptor(customInterceptor);

        Bundle response = null;
        long averageTime = 0L;

        // Setting caching
        CacheControlDirective ccd = new CacheControlDirective();
        boolean cache = false;
        // 3rd iteration, set no cache to true, i.e. cache disabled;
        if (iteration == 3) {
            cache = true;
        }
        ccd.setNoCache(cache);

        // Search for Patient resources
        for (String name : names) {
            response = client
                    .search()
                    .forResource("Patient")
                    .where(Patient.FAMILY.matches().value(name))
                    .cacheControl(ccd) // CacheControl
                    .returnBundle(Bundle.class).execute();
            long timetaken = customInterceptor.responseTime.getMillis();
            patientNamesWithRespTimeMap.put(name, timetaken);
            averageTime += timetaken;
            if (toDisplay) {
                System.out.println("For name: " + name + ", time taken: " + timetaken);
            }
        }

        averageTime = averageTime / names.size();
        if (toDisplay) {
            System.out.println("Total time : " + averageTime);
            System.out.println("Average time : " + averageTime);

            IParser parser = fhirContext.newJsonParser();
            fetchPatientOrderedByPatientFirstName(response, parser);
        }

        // Logging iteration average time
        recordIterationTimeMap.put(iteration, averageTime);
    }

    @Interceptor
    public static class CustomInterceptor {
        public StopWatch responseTime;

        @Hook(Pointcut.CLIENT_RESPONSE)
        public void interceptResponse(IHttpResponse theResponse) {
            responseTime = theResponse.getRequestStopWatch();
        }
    }

    public static void fetchPatientOrderedByPatientFirstName(Bundle bundle, IParser parser) {

        List<BundleEntryComponent> entries = bundle.getEntry();

        List<Resource> patientResources = entries.stream().map(entry -> entry.getResource())
                .collect(Collectors.toList());

        List<Patient> patients = patientResources.stream().map(resource -> {
            return parser.parseResource(Patient.class, parser.encodeResourceToString(resource));
        }).collect(Collectors.toList());

        patients.sort(new PatientComapre());

        patients.forEach(patient -> {
            HumanName name = patient.getNameFirstRep();
            String family = name.getFamily();
            String given = name.getGivenAsSingleString();
            Date birthDate = patient.getBirthDate();
            String birthday = null;
            if (birthDate != null) {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                birthday = format.format(birthDate);
            }
            String totalDetail = given + ", " + family + ", " + birthday;
            patientNames.add(totalDetail);
        });

        patientNames.forEach(System.out::println);
    }

    public static class PatientComapre implements Comparator<Patient> {
        @Override
        public int compare(Patient o1, Patient o2) {
            return o1.getNameFirstRep().getGivenAsSingleString()
                    .compareTo(o2.getNameFirstRep().getGivenAsSingleString());
        }
    }

}
// References: https://hapifhir.io/hapi-fhir/docs/getting_started/introduction.html
