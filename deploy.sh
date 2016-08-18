#!/usr/bin/env bash

set -e

source $BUILD_DIRECTORY/utils/cf-common.sh

function apply_route_service(){
    cmd=$1
    rs=$2
    downstream_svc=$3
    echo running "apply_route_service $cmd $rs $downstream_svc"
    cf a | grep $downstream_svc && {
        downstream_svc_url=`app_domain $downstream_svc`
        echo "we want to $cmd to intercept all requests to $ds whose URL is $downstream_svc_url using route service $rs"
        #cf ds -f $downstream_svc
        IFS='.' read -a fragments <<< "${downstream_svc_url}"
        h=""
        d=""
        for i in "${fragments[@]}"
        do
            [ "$h" == "" ] && h="$i" || d="${d}.${i}"
        done
        d=${d:1:${#d}} # this keeps everything after first '.'
        cf $cmd $d $rs -hostname $h -f
    } || echo "can't find $downstream_svc so not running apply_route_service"
}

# mvn -DskipTests clean install

rs=route-service

# EUREKA SERVICE
#res=routing-eureka-service
#cf ds -f $res
#cf a | grep $res && cf d -f $res
#deploy_app $res
#deploy_service $res
#
## ROUTE SERVICE (https://www.cloudfoundry.org/route-services/)
#rs=route-service
#cf a | grep $rs && cf d -f $rs
#deploy_app $rs
#ad=`app_domain $rs`
#echo $ad
#cf ds -f $rs
#cf s | grep $rs && cf uups $rs -r https://$ad
#cf s | grep $rs || cf cups $rs -r https://$ad

# DOWNSTREAM SERVICE
# https://docs.cloudfoundry.org/services/route-services.html & https://github.com/cloudfoundry-samples/ratelimit-service/blob/master/main.go < rate limiter!
# here we bind the downstream service (by its route) to the route-service we've configured

ds=downstream-service
apply_route_service unbind-route-service $rs $ds
cf a | grep $ds || deploy_app $ds
apply_route_service bind-route-service $rs $ds
