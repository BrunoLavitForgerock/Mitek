package com.mitek.tree;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.mitek.tree.config.Constants;
import com.mitek.tree.nodes.Review;
import com.mitek.tree.util.AccessToken;
import com.mitek.tree.util.HttpConnectionClient;
import com.mitek.tree.util.Images;
import com.mitek.tree.util.VerifyDocument;
import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.CoreWrapper;
import org.junit.Rule;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.security.auth.callback.Callback;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.forgerock.json.JsonValue.*;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.mockito.MockitoAnnotations.initMocks;

public class ReviewITTest {
    @InjectMocks
    Review review;

    @Rule
    public WireMockRule wireMockRule;

    String wireMockPort;


    @Mock
    CoreWrapper coreWrapper;



    @BeforeMethod
    public void before() {
        wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
        wireMockRule.start();
        wireMockPort = String.valueOf(wireMockRule.port());
        initMocks(this);
    }


    @Test
    public void testReviewWithWaitOutcome() throws NodeProcessException {
        review = new Review(new AccessToken(new HttpConnectionClient()),new VerifyDocument(new HttpConnectionClient(),new Images(),coreWrapper));
        wireMockRule.stubFor(post(WireMock.urlPathMatching("/connect/token"))
                .willReturn(aResponse().withBody("{\n" +
                                "  \"access_token\" :\"test123\"\n" +
                                "}")
                        .withStatus(200).withHeader("Content-Type", "application/x-www-form-urlencoded")));
        wireMockRule.stubFor(post(WireMock.urlPathMatching("/api/verify/v2/dossier"))
                .willReturn(aResponse().withBody(response("Successful",true))
                        .withStatus(200).withHeader("Content-Type", "application/x-www-form-urlencoded")));
        HiddenValueCallback hcb1 = new HiddenValueCallback("front");
        HiddenValueCallback hcb2 = new HiddenValueCallback("selfie");
        HiddenValueCallback hcb3 = new HiddenValueCallback("passport");
        HiddenValueCallback hcb4 = new HiddenValueCallback("back");

        hcb1.setValue("data,frontImage");
        hcb2.setValue("data,selfieImage");
        hcb3.setValue("data,passportImage");
        hcb4.setValue("data,backImage");

        List<Callback> cbList = new ArrayList<>();
        HiddenValueCallback hcb = new HiddenValueCallback("isRetake");
        hcb.setValue("false");
        cbList.add(hcb);
        cbList.add(hcb1);
        cbList.add(hcb2);
        cbList.add(hcb3);
        cbList.add(hcb4);
        TreeContext treeContext = buildThreeContext(cbList);
        Action action = review.process(treeContext);
        String outcome = action.outcome;
        Assert.assertEquals(outcome,"Wait");
    }

    @Test
    public void testReviewWithNullAcccessToken(){
        review = new Review(new AccessToken(new HttpConnectionClient()),new VerifyDocument(new HttpConnectionClient(),new Images(),coreWrapper));
        wireMockRule.stubFor(post(WireMock.urlPathMatching("/connect/token"))
                .willReturn(aResponse().withBody("{}")
                        .withStatus(200).withHeader("Content-Type", "application/x-www-form-urlencoded")));

        HiddenValueCallback hcb1 = new HiddenValueCallback("front");
        HiddenValueCallback hcb2 = new HiddenValueCallback("selfie");
        HiddenValueCallback hcb3 = new HiddenValueCallback("passport");
        HiddenValueCallback hcb4 = new HiddenValueCallback("back");

        hcb1.setValue("data,frontImage");
        hcb2.setValue("data,selfieImage");
        hcb3.setValue("data,passportImage");
        hcb4.setValue("data,backImage");

        List<Callback> cbList = new ArrayList<>();
        HiddenValueCallback hcb = new HiddenValueCallback("isRetake");
        hcb.setValue("false");
        cbList.add(hcb);
        cbList.add(hcb1);
        cbList.add(hcb2);
        cbList.add(hcb3);
        cbList.add(hcb4);

        TreeContext treeContext = buildThreeContext(cbList);
        Exception exception = Assert.expectThrows(NodeProcessException.class, () -> {
            review.process(treeContext);
        });

        String expectedMessage = "Caught exception while generating access token, Invalid response from get access token API!";
        String actualMessage = exception.getMessage();
        Assert.assertEquals(actualMessage,expectedMessage);
    }




    private TreeContext buildThreeContext(List<Callback> callbacks) {
        return new TreeContext(retrieveSharedState(), json(object()),
                new ExternalRequestContext.Builder().build(), callbacks
                , Optional.of("mockUserId"));
    }

    private JsonValue retrieveSharedState() {
        return json(object(field(USERNAME, "demo"),
                field(Constants.CLIENT_ID, "clientID"),
                field(Constants.CLIENT_SECRET, "secret"),
                field(Constants.GRANT_TYPE, "testGrant"),
                field(Constants.SCOPE, "test"),
                field(Constants.API_URL,"http://localhost:"+wireMockPort),
                field(Constants.TIMEOUT_VALUE,30),
                field("realm","testRealm")));
    }

    private String response(String processingStatus,boolean isAuthenticated){
        return "{\n" +
                "  \"configuration\": {\n" +
                "    \"responseImages\": [],\n" +
                "    \"verifications\": {}\n" +
                "  },\n" +
                "  \"dossierMetadata\": {\n" +
                "    \"createdDateTime\": \"2022-07-12T20:15:57.8771097Z\",\n" +
                "    \"dossierId\": \"d26fbac6-3de3-4ac5-9c8d-93a020eef02e\",\n" +
                "    \"processingStatus\": \"Successful\",\n" +
                "    \"version\": \"v2.1.0\"\n" +
                "  },\n" +
                "  \"evidence\": [\n" +
                "    {\n" +
                "      \"evidenceId\": \"4ef5e5ff-047b-411a-87c5-656810f4b09a\",\n" +
                "      \"extractedData\": {\n" +
                "        \"address\": {\n" +
                "          \"addressLine1\": \"2570 24TH STREET\",\n" +
                "          \"city\": \"SACRAMENTO\",\n" +
                "          \"dynamicProperties\": {},\n" +
                "          \"postalCode\": \"95822\",\n" +
                "          \"stateProvince\": \"CA\"\n" +
                "        },\n" +
                "        \"dateOfBirth\": \"1977-08-31\",\n" +
                "        \"dateOfExpiry\": \"2014-08-31\",\n" +
                "        \"documentNumber\": \"I1234562\",\n" +
                "        \"dynamicProperties\": {\n" +
                "          \"sex\": \"M\"\n" +
                "        },\n" +
                "        \"name\": {\n" +
                "          \"dynamicProperties\": {},\n" +
                "          \"fullName\": \"ALEXANDER J SAMPLE\",\n" +
                "          \"givenNames\": \"ALEXANDER J\",\n" +
                "          \"surname\": \"SAMPLE\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"images\": [\n" +
                "        {\n" +
                "          \"classification\": {\n" +
                "            \"imageType\": \"DriversLicenseFront\",\n" +
                "            \"mdsid\": \"MDS.2.0.USA.CA.DL.STD.012009.01\"\n" +
                "          },\n" +
                "          \"derivedImages\": {},\n" +
                "          \"extractedData\": {\n" +
                "            \"address\": {\n" +
                "              \"addressLine1\": \"2570 24TH STREET\",\n" +
                "              \"city\": \"SACRAMENTO\",\n" +
                "              \"dynamicProperties\": {},\n" +
                "              \"postalCode\": \"95822\",\n" +
                "              \"stateProvince\": \"CA\"\n" +
                "            },\n" +
                "            \"dateOfBirth\": \"1977-08-31\",\n" +
                "            \"dateOfExpiry\": \"2014-08-31\",\n" +
                "            \"documentNumber\": \"I1234562\",\n" +
                "            \"dynamicProperties\": {\n" +
                "              \"ocrCity\": \"SACRAMENTO\",\n" +
                "              \"ocrDateOfBirth\": \"1977-08-31\",\n" +
                "              \"ocrDateOfExpiry\": \"2014-08-31\",\n" +
                "              \"ocrDocumentNumber\": \"I1234562\",\n" +
                "              \"ocrFirstName\": \"ALEXANDER\",\n" +
                "              \"ocrMiddleName\": \"J\",\n" +
                "              \"ocrPostalCode\": \"95822\",\n" +
                "              \"ocrSex\": \"M\",\n" +
                "              \"ocrState\": \"CA\",\n" +
                "              \"ocrStreetName\": \"24TH STREET\",\n" +
                "              \"ocrStreetNumber\": \"2570\",\n" +
                "              \"ocrSurname\": \"SAMPLE\",\n" +
                "              \"sex\": \"M\"\n" +
                "            },\n" +
                "            \"name\": {\n" +
                "              \"dynamicProperties\": {},\n" +
                "              \"fullName\": \"ALEXANDER J SAMPLE\",\n" +
                "              \"givenNames\": \"ALEXANDER J\",\n" +
                "              \"surname\": \"SAMPLE\"\n" +
                "            }\n" +
                "          },\n" +
                "          \"imageId\": \"7b8acd89-a6ab-41ea-9370-2b3aabcc6d32\",\n" +
                "          \"processingReasons\": {},\n" +
                "          \"processingStatus\": \"Successful\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"classification\": {\n" +
                "            \"imageType\": \"DriversLicenseBack\"\n" +
                "          },\n" +
                "          \"derivedImages\": {},\n" +
                "          \"imageId\": \"98ed5dec-4a82-4723-88ee-9edc256cf01f\",\n" +
                "          \"processingReasons\": {\n" +
                "            \"502\": \"The barcode could not be parsed\"\n" +
                "          },\n" +
                "          \"processingStatus\": " +processingStatus+
                "        }\n" +
                "      ],\n" +
                "      \"type\": \"IdDocument\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"findings\": {\n" +
                "    \"authenticated\": "+isAuthenticated+",\n" +
                "    \"probability\": 0,\n" +
                "    \"verifications\": [\n" +
                "      {\n" +
                "        \"documentId\": \"7b8acd89-a6ab-41ea-9370-2b3aabcc6d32\",\n" +
                "        \"judgement\": \"Authentic\",\n" +
                "        \"name\": \"Black And White Copy\",\n" +
                "        \"notifications\": {},\n" +
                "        \"probability\": 819,\n" +
                "        \"verificationType\": 102,\n" +
                "        \"version\": \"3.44.0.6399\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"documentId\": \"7b8acd89-a6ab-41ea-9370-2b3aabcc6d32\",\n" +
                "        \"judgement\": \"Authentic\",\n" +
                "        \"name\": \"Image Classification\",\n" +
                "        \"notifications\": {},\n" +
                "        \"probability\": 1000,\n" +
                "        \"verificationType\": 105,\n" +
                "        \"version\": \"3.44.0.6399\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"documentId\": \"7b8acd89-a6ab-41ea-9370-2b3aabcc6d32\",\n" +
                "        \"judgement\": \"Authentic\",\n" +
                "        \"name\": \"Human Face Presence\",\n" +
                "        \"notifications\": {},\n" +
                "        \"probability\": 996,\n" +
                "        \"verificationType\": 300,\n" +
                "        \"version\": \"3.44.0.6399\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"documentId\": \"7b8acd89-a6ab-41ea-9370-2b3aabcc6d32\",\n" +
                "        \"judgement\": \"Fraudulent\",\n" +
                "        \"name\": \"Field Validation\",\n" +
                "        \"notifications\": {},\n" +
                "        \"probability\": 0,\n" +
                "        \"verificationType\": 707,\n" +
                "        \"version\": \"3.44.0.6399\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"documentId\": \"7b8acd89-a6ab-41ea-9370-2b3aabcc6d32\",\n" +
                "        \"judgement\": \"Authentic\",\n" +
                "        \"name\": \"ID Document Blacklist\",\n" +
                "        \"notifications\": {},\n" +
                "        \"probability\": 1000,\n" +
                "        \"verificationType\": 101,\n" +
                "        \"version\": \"3.44.0.6399\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"documentId\": \"7b8acd89-a6ab-41ea-9370-2b3aabcc6d32\",\n" +
                "        \"judgement\": \"Authentic\",\n" +
                "        \"name\": \"Generic Font\",\n" +
                "        \"notifications\": {},\n" +
                "        \"probability\": 789,\n" +
                "        \"verificationType\": 104,\n" +
                "        \"version\": \"3.44.0.6399\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"documentId\": \"7b8acd89-a6ab-41ea-9370-2b3aabcc6d32\",\n" +
                "        \"judgement\": \"Fraudulent\",\n" +
                "        \"name\": \"Rounded Corner Presence\",\n" +
                "        \"notifications\": {},\n" +
                "        \"probability\": 205,\n" +
                "        \"verificationType\": 100,\n" +
                "        \"version\": \"3.44.0.6399\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"documentId\": \"98ed5dec-4a82-4723-88ee-9edc256cf01f\",\n" +
                "        \"judgement\": \"Fraudulent\",\n" +
                "        \"name\": \"Image Classification\",\n" +
                "        \"notifications\": {},\n" +
                "        \"probability\": 0,\n" +
                "        \"verificationType\": 105,\n" +
                "        \"version\": \"3.44.0.6399\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"documentId\": \"98ed5dec-4a82-4723-88ee-9edc256cf01f\",\n" +
                "        \"judgement\": \"Undetermined\",\n" +
                "        \"name\": \"Barcode Analysis\",\n" +
                "        \"notifications\": {\n" +
                "          \"699\": \"The authenticator is not available for this document.\"\n" +
                "        },\n" +
                "        \"probability\": 0,\n" +
                "        \"verificationType\": 611,\n" +
                "        \"version\": \"1.1\"\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}";
    }
}
