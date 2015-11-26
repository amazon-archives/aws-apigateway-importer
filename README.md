# Amazon API Gateway Importer

The **Amazon API Gateway Importer** lets you create or update [Amazon API Gateway][service-page] APIs from a Swagger or RAML API representation.

To learn more about API Gateway, please see the [service documentation][service-docs] or the [API documentation][api-docs].

[service-page]: http://aws.amazon.com/api-gateway/
[service-docs]: http://docs.aws.amazon.com/apigateway/latest/developerguide/
[api-docs]: http://docs.aws.amazon.com/apigateway/api-reference

[![Build Status](https://api.travis-ci.org/awslabs/aws-apigateway-swagger-importer.svg?branch=master)](https://travis-ci.org/awslabs/aws-apigateway-swagger-importer)

## Usage

### Prerequisites

#### Credentials
This tool requires AWS credentials to be configured in at least one of the locations specified by the [default credential provider chain](http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/credentials.html)

It will look for configured credentials in environment variables, Java system properties, [AWS SDK/CLI](http://aws.amazon.com/cli) profile credentials, and EC2 instance profile credentials.

#### Build

Build with `mvn assembly:assembly`

### Import a new API

```sh
./aws-api-import.sh --create path/to/swagger.json

./aws-api-import.sh -c path/to/api.raml
```

### Update an existing API and deploy it to a stage

```sh
./aws-api-import.sh --update API_ID --deploy STAGE_NAME path/to/swagger.yaml

./aws-api-import.sh --update API_ID --deploy STAGE_NAME --raml-config path/to/config.json path/to/api.raml
```

For Windows environments replace `./aws-api-import.sh` with `./aws-api-import.cmd` in the examples.

### API Gateway Extension Example

You can fully define an API Gateway API in Swagger using the `x-amazon-apigateway-auth` and `x-amazon-apigateway-integration` extensions,
or in RAML using an external configuration file.

Defined on an Operation:

```json

"x-amazon-apigateway-auth" : {
    "type" : "aws_iam"
},
"x-amazon-apigateway-integration" : {
   "type" : "aws",
   "uri" : "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:MY_ACCT_ID:function:helloWorld/invocations",
   "httpMethod" : "POST",
   "credentials" : "arn:aws:iam::MY_ACCT_ID:role/lambda_exec_role",
   "requestTemplates" : {
       "application/json" : "json request template 2",
       "application/xml" : "xml request template 2"
   },
   "requestParameters" : {
       "integration.request.path.integrationPathParam" : "method.request.querystring.latitude",
       "integration.request.querystring.integrationQueryParam" : "method.request.querystring.longitude"
   },
   "cacheNamespace" : "cache-namespace",
   "cacheKeyParameters" : [],
   "responses" : {
       "2\\d{2}" : {
           "statusCode" : "200",
           "responseParameters" : {
               "method.response.header.test-method-response-header" : "integration.response.header.integrationResponseHeaderParam1"
           },
           "responseTemplates" : {
               "application/json" : "json 200 response template",
               "application/xml" : "xml 200 response template"
           }
       },
       "default" : {
           "statusCode" : "400",
           "responseParameters" : {
               "method.response.header.test-method-response-header" : "'static value'"
           },
           "responseTemplates" : {
               "application/json" : "json 400 response template",
               "application/xml" : "xml 400 response template"
           }
       }
   }
}
```

## Testing

```sh
mvn test
```
