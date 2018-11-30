# lightcone

## Compiling
* Compile all, run `sbt compile`
* Compile certain subproject, run `sbt "project $projectName" compile`

## Testing
* To generate test coverage report, run `sbt coverageReport`
* Run test under specific subproject, run `sbt "project $projectName" test`, i.e. `sbt "project ethereum" test`

