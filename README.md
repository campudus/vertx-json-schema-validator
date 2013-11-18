#vertx-json-schema-validator

Vertx module to validate JSON against a schema (see http://json-schema.org/)

This module is based on the Java implementation of JSON-Schema by Francis Galiegue (see https://github.com/fge/json-schema-validator)

## Requirements
* Vert.x 2.1M1+

##Installation
`vertx install com.campudus.~json-schema-validator~0.1.0-SNAPSHOT`

## Configuration

    {
      "address" : <event-bus-addres-to-listen-on>,
      "schemas" : [{"schema" : <yourJsonSchema>},{"key" : <keyForYourJsonSchema>, "schema" : <yourJsonSchema>}]
    }

* `address` - The address this module should register on the event bus. Defaults to `campudus.jsonvalidator`
* `schemas` - This is an Array of schemas which are available to check against. Every schema in this Array is described by a JsonObject which should look like following:

```
    {
      "key" : <keyForYourJsonSchema>,
      "schema" : <yourJsonSchema>
    }
```

* `key` - This is a key for this schema which is later used to define which schema should be used to check your JSON against. The key is optional. If you don't define a key, the key will be generated as: `schema<index>` where `index` is the position in the array of this schema.
* `schema` - This is the JsonSchema Object which describes your JsonSchema (see http://json-schema.org/)

###Example Configuration
    {
      "schemas" : [
        {
          "key" : "simpleSchema",
          "schema" : {
            "$schema": "http://json-schema.org/draft-04/schema#",
            "title": "Example Schema",
            "type": "object",
            "properties": {
              "firstName": {
                "type": "string"
              },
              "lastName": {
                "type": "string"
              },
              "age": {
                "description": "Age in years",
                "type": "integer",
                "minimum": 0
              }
            },
            "required": ["firstName", "lastName"]
          }
        }
      ]
    }

## Usage
Currently there are two commands for this module.

###Validate Json

Use this action to validate a Json against a JsonSchema.

    {
      "action" : "validate",
      "key" : "simpleSchema",
      "json" : {
        "firstName" : "Hans",
        "lastName" : "Dampf"
      }
    }

* `action` - Always `validate` for validating a Json
* `key` - The key to the JsonSchema to validate against
* `json` - The Json which should be validated

###Get all schema keys

Use this action to get all registered schema keys

    {
      "action" : "getSchemaKeys",
    }

###Reply messages
The module will reply to all requests.  In the message, there will be either a `"status" : "ok"` or a `"status" : "error"`.

####Reply to `validate` action
If the request could be processed without problems, it will result in an "ok" status. See an example here:

    {
      "status" : "ok",
    }

If the request resulted in an error, a possible reply message looks like this:

    {
      "status" : "error",
      "error" : <ERROR_KEY>,
      "message" : <ERROR_MESSAGE>,
      "report" : <VALIDATION_REPORT>
    }

* `error` - Possible error keys are: `MISSING_JSON` `INVALID_SCHEMA_KEY` `MISSING_SCHEMA_KEY` `VALIDATION_ERROR`, `INVALID_JSON`
* `message` - The message which describes the error
* `report` - This field is only present when the validation failed. A report can look like (see https://github.com/fge/json-schema-validator):

```
    [ {
      "level" : "error",
      "schema" : {
        "loadingURI" : "#",
        "pointer" : ""
      },
      "instance" : {
        "pointer" : ""
      },
      "domain" : "validation",
      "keyword" : "required",
      "message" : "missing required property(ies)",
      "required" : [ "firstName", "lastName" ],
      "missing" : [ "lastName" ]
    } ]
```

####Reply to `getSchemaKeys` action
The request will result in an "ok" status and a JsonArray `schemas` with the schema keys. See an example here:

    {
      "status" : "ok",
      "schemas" : ["simpleSchema", "complexSchema"]
    }

##Licence

This project is freely available under the Apache 2 licence, fork, fix and send back :)
