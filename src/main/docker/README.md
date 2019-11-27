#Docker compose

##Development:

1. Add to hosts file:

    ```
    127.0.0.1 jhipster-registry
    127.0.0.1 ecos-registry
    ```

    **Note:** 
    We run microservices from  different sides: 
    * in the docker container 
    * as the spring boot app (only for development) 
    
    All microservices must be registered in ecos-registry. The path for 
    registration is determined by _central-server-config_ _eureka.client.service-url.defaultZone_ props, _docker-config_ for 
    docker microservices, _localhost-config_ for localhost microservices, but not both. Easy way to solve this problem - _docker-config_ and 
    add mapping to hosts file.
2. Create volumes for postgresql.
    ```
    docker volume create --name=rabbitmq_data
    docker volume create --name=psql_eapps
    docker volume create --name=psql_emodel
    ```
3. Standard start alfresco-ecos on _8080_ port
4. Start docker-compose
4. Run microservice to develop by spring boot app.
