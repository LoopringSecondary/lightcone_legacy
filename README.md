# lightcone

## Compiling
* Compile all, run `sbt test:compile`
* Compile subproject, run `sbt $projectName/test:compile`

## Testing
* You need to install docker to run all tests
* To run tests under specific subproject, run `sbt $projectName/test`
* To run only modified and failed tests, run `sbt testQuick`
* To generate test coverage report, run `sbt coverageReport`

