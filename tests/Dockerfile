FROM registry.access.redhat.com/ubi8:8.10-1020
ARG OC_CLI_URL=https://mirror.openshift.com/pub/openshift-v4/amd64/clients/ocp/4.14.33/openshift-client-linux.tar.gz
ARG TRUSTYAI_TESTS_ORG=trustyai-explainability
ARG TRUSTYAI_TESTS_REPO=trustyai-tests
ARG TRUSTYAI_TESTS_BRANCH=main

ENV HOME /root
WORKDIR /root

RUN dnf install -y jq bc git go-toolset python3.11 python3.11-devel python3.11-pip unzip && \
    dnf clean all && \
    git clone https://github.com/opendatahub-io/peak $HOME/peak && \
    cd $HOME/peak && \
    git submodule update --init

# install yq to help with parsing json
RUN curl -L https://github.com/mikefarah/yq/releases/download/v4.25.1/yq_linux_amd64 -o /usr/bin/yq &&\
    chmod +x /usr/bin/yq

# Use a specific destination file name in case the url dow download name changes
ADD ${OC_CLI_URL} $HOME/peak/oc-cli.tar.gz
RUN tar -C /usr/local/bin -xvf $HOME/peak/oc-cli.tar.gz && \
    chmod +x /usr/local/bin/oc

# Install poetry to support the exeuction of trustyai-tests
RUN curl -sSL https://install.python-poetry.org | python3 -
ENV PATH="${PATH}:$HOME/.local/bin"

# break cache if the repo has changed
ADD https://api.github.com/repos/${TRUSTYAI_TESTS_ORG}/${TRUSTYAI_TESTS_REPO}/git/refs/heads/${TRUSTYAI_TESTS_BRANCH} version.json
RUN cd $HOME/peak && \
    git clone -b ${TRUSTYAI_TESTS_BRANCH} https://github.com/${TRUSTYAI_TESTS_ORG}/${TRUSTYAI_TESTS_REPO}.git && \
    cd trustyai-tests && \
    poetry install

COPY installandtest.sh custom_operators_config*.yaml $HOME/peak/

RUN mkdir -p $HOME/.kube && \
    chmod -R 777 $HOME/.kube && \
    chmod -R 777 $HOME/peak && \
    mkdir -p /peak && \
    chmod -R 777 $HOME && \
    ln -s $HOME/peak/installandtest.sh /peak/installandtest.sh

# For local testing, you can add your own kubeconfig to the image
# Note:  Do not push the image to a public repo with your kubeconfig
# ADD kubeconfig /root/.kube/config

CMD $HOME/peak/installandtest.sh

# This is a dummy PR to test the CI
