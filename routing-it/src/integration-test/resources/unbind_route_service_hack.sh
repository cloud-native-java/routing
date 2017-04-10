#!/usr/bin/env bash

function install_cf(){
    mkdir -p $HOME/bin
    curl -v -L -o cf.tgz 'https://cli.run.pivotal.io/stable?release=linux64-binary&version=6.23.1&source=github-rel'
    tar zxpf cf.tgz
    mkdir -p $HOME/bin && mv cf $HOME/bin
}

function validate_cf(){

    cf  -v || install_cf

    export PATH=$PATH:$HOME/bin

    cf api https://api.run.pivotal.io
    cf auth $CF_USER "$CF_PASSWORD" && \
    cf target -o $CF_ORG -s $CF_SPACE && \
    cf apps
}

install_cf

validate_cf

cf unbind-route-service cfapps.io route-service-svc -n $1 -f
