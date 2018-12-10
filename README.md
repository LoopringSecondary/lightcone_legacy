# Lightcone Relay

## Compile

* Compile all, run `sbt test:compile`
* Compile subproject, run `sbt $projectName/test:compile`
> Make sure to use `test:compile` instead of `compile` so all tests compile.

## Dockerize

You must have Docker and Docker-Compose installed.

* To create a docker iamge file for the runnable program (in subproject actors), run `sbt docker`. A docker image with name "org.loopring/lightcone:latest" will be generated.
* To publish the docker image, use `sbt dockerPush`.


## Test
* You need to install docker to run all tests
* To run tests under specific subproject, run `sbt $projectName/test`
* To run only modified and failed tests, run `sbt testQuick`
* To generate test coverage report, run `sbt coverageReport`

