# lightcone

## Compiling
* Compile all, run `sbt compile`
* Compile subproject, run `sbt project/$projectName`

## Testing
* You need to install docker to run all tests
* To run tests under specific subproject, run `sbt $projectName/test`
* To run only modified and failed tests, run `sbt testQuick`
* To generate test coverage report, run `sbt coverageReport`

