import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;

public class SampleClient {

    public static void main(String[] theArgs) {

        // Create a FHIR client
        FhirContext fhirContext = FhirContext.forR4();
        IGenericClient client = fhirContext.newRestfulGenericClient("http://hapi.fhir.org/baseR4");
        client.registerInterceptor(new LoggingInterceptor(false));

        // Search for Patient resources
        Bundle response = client
                .search()
                .forResource("Patient")
                .where(Patient.FAMILY.matches().value("SMITH"))
                .returnBundle(Bundle.class)
                .execute();

        IParser parser = fhirContext.newJsonParser();

        fetchPatientOrderedByPatientFirstName(response, parser);
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
            System.out.println(totalDetail);
        });

    }

    public static class PatientComapre implements Comparator<Patient> {
        @Override
        public int compare(Patient o1, Patient o2) {
            return o1.getNameFirstRep().getGivenAsSingleString()
                    .compareTo(o2.getNameFirstRep().getGivenAsSingleString());
        }
    }

}
