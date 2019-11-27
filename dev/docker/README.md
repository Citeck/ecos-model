#Docker compose for development current microservice

##To start development:

1. Add to hosts file:

    ```
    127.0.0.1 jhipster-registry
    ```

    **Note:** we run microservices from  different sides - from the docker container and just start the spring boot app 
    (current microservices for development). All microservices must be registered in jhipster registry. The path for 
    registration is determined by _central-server-config_ _eureka.client.service-url.defaultZone_ props, _docker-config_ for 
    docker microservices, _localhost-config_ for localhost microservices, but not both. Easy way to solve this problem - _docker-config_ and 
    add mapping to hosts file.
2. Create volumes for postgresql. [See bug on windows docker](https://github.com/docker/for-win/issues/445).
    ```
    docker volume create psql_gateway
    docker volume create psql_uiserv
    etc...
    ```
3. Standard start alfresco-ecos on _8080_ port
4. Start docker-compose
4. Run microservice to develop by spring boot app.
