#!/bin/sh

echo "The application will start in ${ECOS_MODEL_SLEEP:-JHIPSTER_SLEEP}s..." && sleep ${ECOS_MODEL_SLEEP:-JHIPSTER_SLEEP}
exec java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -jar "${HOME}/app.war" "$@"
