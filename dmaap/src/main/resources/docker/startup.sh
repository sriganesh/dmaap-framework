#*******************************************************************************
# BSD License
#  
# Copyright (c) 2016, AT&T Intellectual Property.  All other rights reserved.
#  
# Redistribution and use in source and binary forms, with or without modification, are permitted
# provided that the following conditions are met:
#  
# 1. Redistributions of source code must retain the above copyright notice, this list of conditions
#    and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
#    conditions and the following disclaimer in the documentation and/or other materials provided
#    with the distribution.
# 3. All advertising materials mentioning features or use of this software must display the
#    following acknowledgement:  This product includes software developed by the AT&T.
# 4. Neither the name of AT&T nor the names of its contributors may be used to endorse or
#    promote products derived from this software without specific prior written permission.
#  
# THIS SOFTWARE IS PROVIDED BY AT&T INTELLECTUAL PROPERTY ''AS IS'' AND ANY EXPRESS OR
# IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
# SHALL AT&T INTELLECTUAL PROPERTY BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
# PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;  LOSS OF USE, DATA, OR PROFITS;
# OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
# CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
# ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
# DAMAGE.
#*******************************************************************************
root_directory="/appl/${project.artifactId}"
config_directory="/appl/${project.artifactId}/bundleconfig"
runner_file="appl/${project.artifactId}/lib/ajsc-runner-${ajscRuntimeVersion}.jar"
echo "AJSC HOME directory is " $root_directory
echo "AJSC Conf Directory is" $config_directory
echo "Starting using" $runner_file

java -jar  -XX:MaxPermSize=256m -XX:PermSize=32m -DSOACLOUD_SERVICE_VERSION=0.0.1 -DAJSC_HOME=$root_directory -DAJSC_CONF_HOME=$config_directory -DAJSC_SHARED_CONFIG=$config_directory -DAJSC_HTTPS_PORT=3905 -Dplatform=NON-PROD -DPid=1306 -Dlogback.configurationFile=/appl/dmaapMR1/bundleconfig/etc/logback.xml -Xmx512m -Xms512m  $runner_file context=/ port=3904 sslport=3905
