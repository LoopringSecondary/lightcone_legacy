# lightcone

## Compiling
* Compile all, run `sbt test:compile`
* Compile subproject, run `sbt $projectName/test:compile`
> Make sure to use `test:compile` instead of `compile` so all tests compile.

## Testing
* You need to install docker to run all tests
* To run tests under specific subproject, run `sbt $projectName/test`
* To run only modified and failed tests, run `sbt testQuick`
* To generate test coverage report, run `sbt coverageReport`

