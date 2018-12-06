# Lightcone Relay

## Compile

* Compile all, run `sbt compile`
* Compile certain subproject, run `sbt "project $projectName" compile`

## Dockerize

* To create a docker iamge file for the runnable program (in subproject actors), run `sbt docker`

## Test

* To generate test coverage report, run `sbt coverageReport`
* Run test under specific subproject, run `sbt "project $projectName" test`, i.e. `sbt "project ethereum" test`

