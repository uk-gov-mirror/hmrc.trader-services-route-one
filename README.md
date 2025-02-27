![GitHub release (latest by date)](https://img.shields.io/github/v/release/hmrc/trader-services-route-one) ![GitHub code size in bytes](https://img.shields.io/github/languages/code-size/hmrc/trader-services-route-one)

# trader-services-route-one

Backend microservice exposing an API of Trader Services on MDTP.

## API

### Create Case

Method | Path | Description | Authorization
---|---|---|---
`POST` | `/create-case` | create new case in the PEGA system or report duplicate | any GovernmentGateway authorized user

Header | Description
---|---
`x-correlation-id` | message correlation UUID (optional)

Response status | Description
---|---
201| when created, body payload will be `{ "result" : "$CaseID" }`
400| when payload invalid or has not passed the validation
409| when duplicate case

Example request payload 

    {
        "entryDetails" : {
            "epu" : "123",
            "entryNumber" : "000000Z",
            "entryDate" : "2020-10-05"
        },
        "questionsAnswers" : {
            "export" : {
            "requestType" : "New",
            "routeType" : "Route2",
            "freightType" : "Air",
            "vesselDetails" : {
                "vesselName" : "Foo Bar",
                "dateOfArrival" : "2020-10-19",
                "timeOfArrival" : "10:09:00"
            },
            "contactInfo" : {
                "contactName" : "Bob",
                "contactEmail" : "name@somewhere.com",
                "contactNumber" : "01234567891"
            }
            }
        },
        "uploadedFiles" : [ {
            "downloadUrl" : "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            "uploadTimestamp" : "2018-04-24T09:30:00Z",
            "checksum" : "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
            "fileName" : "test.pdf",
            "fileMimeType" : "application/pdf"
        } ],
        "eori" : "GB123456789012345" <- optional
    }

Example 201 success response payload

    {
        "correlationId" : "4327cf1f-5bcc-4c4a-acae-391588567d87",
        "result" : {
          "caseId" : "330XGBNZJO04",
          "generatedAt" : "2020-11-03T15:29:28.601Z"
          }
    }

Example 400 error response payload

    {
        "correlationId" : "7fedc2d5-1bba-434b-87e6-4d4ec1757e31",
        "error" : {
            "errorCode" : "400",
            "errorMessage" : "invalid phone number"
        }
    } 

Example 409 duplicate case error payload

    {
        "correlationId" : "7fedc2d5-1bba-434b-87e6-4d4ec1757e31",
        "error" : {
            "errorCode" : "409",
            "errorMessage" : "XYZ1234567890"
        }
    }

### Update Case

Method | Path | Description | Authorization
---|---|---|---
`POST` | `/update-case` | update existing case in the PEGA system | any GovernmentGateway authorized user

Header | Description
---|---
`x-correlation-id` | message correlation UUID (optional)

Response status | Description
---|---
201| when updated, body payload will be `{ "result" : "$CaseID" }`
400| when payload invalid or has not passed the validation

Example request payload 

    {
        "caseReferenceNumber": "PCE201103470D2CC8K0NH3",
        "typeOfAmendment": "WriteResponseAndUploadDocuments",
        "responseText":"An example response.",
        "uploadedFiles" : [ {
            "downloadUrl" : "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            "uploadTimestamp" : "2018-04-24T09:30:00Z",
            "checksum" : "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
            "fileName" : "test.pdf",
            "fileMimeType" : "application/pdf"
        } ],
        "eori" : "GB123456789012345" <- optional
    }

Example 201 success response payload

    {
        "correlationId" : "4327cf1f-5bcc-4c4a-acae-391588567d87",
        "result" : {
          "caseId" : "330XGBNZJO04",
          "generatedAt" : "2020-11-03T15:29:28.601Z"
          }
    }

Example 400 error response payload

    {
        "correlationId" : "7fedc2d5-1bba-434b-87e6-4d4ec1757e31",
        "error" : {
            "errorCode" : "400",
            "errorMessage" : "invalid phone number"
        }
    } 

### Transfer File

Method | Path | Description | Authorization
---|---|---|---
`POST` | `/transfer-file` | transfer file to the PEGA system | any GovernmentGateway authorized user

Header | Description
---|---
`x-correlation-id` | message correlation UUID (optional)

Response status | Description
---|---
202| when file transfer successful
400| when payload invalid or has not passed the validation

Example request payload:

    {
        "conversationId":"074c3823-c941-417e-a08b-e47b08e9a9b7",
        "caseReferenceNumber":"Risk-123",
        "applicationName":"Route1",
        "upscanReference":"XYZ0123456789",
        "downloadUrl":"https://s3.amazonaws.com/bucket/9d9e1444-2555-422e-b251-44fd2e85530a",
        "fileName":"test.jpeg",
        "fileMimeType":"image/jpeg",
        "checksum":"a38d7dd155b1ec9703e5f19f839922ad5a1b0aa4f255c6c2b03e61535997d757",
        "batchSize": 1,
        "batchCount": 1
    }

Example 400 error response payload

    {
        "correlationId" : "7fedc2d5-1bba-434b-87e6-4d4ec1757e31",
        "error" : {
            "errorCode" : "400",
            "errorMessage" : "invalid case reference number"
        }
    }     


## Running the tests

    sbt test it:test

## Running the tests with coverage

    sbt clean coverageOn test it:test coverageReport

## Running the app locally

    sm --start TRADER_SERVICES_ALL
    sm --stop TRADER_SERVICES_ROUTE_ONE
    sbt run

It should then be listening on port 9380

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
