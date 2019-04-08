[![Build Status](https://semaphoreci.com/api/v1/projects/adecda77-2a3e-434e-8517-53a7dfd94586/2473633/shields_badge.svg)](https://semaphoreci.com/loopring/lightcone)

# Lightcone Relayer 1.0

This relayer is implemented to support Loopring Protocol 2.x. A [new version](https://github.com/Loopring/lightcone2) is being worked on for [Loopring Protocol 3.0](https://github.com/Loopring/protocols/tree/master/packages/loopring_v3). 

## Compile

* Compile all, run `sbt test:compile`
* Remove all unused imports, run `sbt fix`

* Compile subproject, run `sbt $projectName/test:compile`
> Make sure to use `test:compile` instead of `compile` so all tests compile.

* Before committing to git, use `sbt check`.
* Check scala styles for optimization and refactoring, use `sbt scapegoat`.

## Dockerize

You must have Docker and Docker-Compose installed.

* To create a docker image file for the runnable program (in subproject relayer), run `sbt docker`. A docker image with name "org.loopring/lightcone_relayer:latest" will be generated.
* To publish the docker image, use `sbt dockerPush`.
* To run in docker, run `docker run --add-host mysql-host:xx.xx.xx.xx --add-host ethereum-host:xx.xx.xx.xx -P -v /xxx/log:/log --shm-size 358716160 -d org.loopring/lightcone_relayer:latest`
* To run in docker-compose, run `docker-compose up --scale worker=3`


## Test
* You need to install docker to run all tests
* To run tests under specific subproject, run `sbt $projectName/test`
* To run only modified and failed tests, run `sbt testQuick`
* To generate test coverage report, run `sbt coverageReport`

