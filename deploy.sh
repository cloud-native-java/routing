#!/usr/bin/env bash

set -e

source $BUILD_DIRECTORY/utils/cf-common.sh
#
#cs=configuration-service
#rb=rabbitmq-bus
#
#cf s | grep $rb || cf cs cloudamqp lemur $rb
#
#cf d -f configuration-client
#cf d -f $cs
#cf s | grep $cs && cf ds -f $cs
#
#deploy_app $cs
#deploy_service $cs
#deploy_app configuration-client



# first test: deploy the downstream service

#lrs=logging-route-service
#mvn -DskipTests clean install
#cf push -p target/route-service.jar $lrs
#cf create-user-provided-service test-route-service -r https://<ROUTE-SERVICE-ADDRESS>


# EUREKA SERVICE
res=routing-eureka-service
cf a | grep $res && cf d -f $res
deploy_app $res
deploy_service $res

# ROUTE SERVICE
rs=route-service
deploy_app $rs
# now we have the actual logging RS, let's CUPS it and bind that to something else
ad=`app_domain $rs`
echo $ad
cf cups $rs -r https://$ad
