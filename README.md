# ECOS-MODEL

### Development

To start your application in the dev profile, simply run:

    mvn spring-boot:run


### Building for production

To optimize the emodel application for production, run:

    mvn clean package

To ensure everything worked, run:

    mvn spring-boot:run -Pprod


### Testing

To launch your application's tests, run:

    mvn test


### Code quality

Description in progress... 


### Using docker-compose

You can fully dockerize your application and all the services that it depends on.
To achieve this, first build a docker image of your app by running:

    mvn jib:dockerBuild

Then run:

    docker-compose -f src/main/docker/app.yml up -d
