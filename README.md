
[![CircleCI](https://circleci.com/gh/Loopring/lightcone.svg?style=svg&circle-token=b0b326cac389ea05a6dc019b83a2691f8596688b)](https://circleci.com/gh/Loopring/lightcone)
# Lightcone Relay

## Compile

* Compile all, run `sbt test:compile`
* Compile subproject, run `sbt $projectName/test:compile`
> Make sure to use `test:compile` instead of `compile` so all tests compile.

## Dockerize

You must have Docker and Docker-Compose installed.

* To create a docker image file for the runnable program (in subproject actors), run `sbt docker`. A docker image with name "org.loopring/lightcone:latest" will be generated.
* To publish the docker image, use `sbt dockerPush`.
* To run in docker, run `docker run -P -v /xxx/log:/log --shm-size 358716160 -d org.loopring/lightcone_actors:latest`
* To run in docker-compose, run `docker-compose up --scale worker=3`


## Test
* You need to install docker to run all tests
* To run tests under specific subproject, run `sbt $projectName/test`
* To run only modified and failed tests, run `sbt testQuick`
* To generate test coverage report, run `sbt coverageReport`

