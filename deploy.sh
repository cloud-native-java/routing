#!/usr/bin/env bash
set -e

source $BUILD_DIRECTORY/utils/cf-common.sh

mvn -DskipTests clean install

# EUREKA SERVICE
res=routing-eureka-service
cf ds -f $res
cf a | grep $res && cf d -f $res
deploy_app $res
deploy_service $res

# ROUTE SERVICE (https://www.cloudfoundry.org/route-services/)
rs=route-service
cf a | grep $rs && cf d -f $rs
deploy_app $rs
ad=`app_domain $rs`
echo $ad
cf ds -f $rs
cf cups $rs -r https://$ad

# DOWNSTREAM SERVICE
## here we bind the downstream service (by its route)
## to the route-service we've configured
ds=downstream-service
cf a | grep $ds && cf d -f $ds
deploy_app $ds
url=`app_domain $ds`
IFS='.' read -a fragments <<< "${url}"
h=""
d=""
for i in "${fragments[@]}"
do
  [ "$h" == "" ] && h="$i" || d="${d}.${i}"
done
d=${d:1:${#d}} # this keeps everything after first '.'
cf bind-route-service $d $rs -hostname $h