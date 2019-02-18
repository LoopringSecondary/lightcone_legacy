#!/bin/sh

web3j solidity generate -b build/RingSubmitter.bin -a build/RingSubmitter.abi -o . -p io.lightcone.ethereum.contract
web3j solidity generate -b build/OrderCanceller.bin -a build/OrderCanceller.abi -o . -p io.lightcone.ethereum.contract
web3j solidity generate -b build/ERC20Token.bin -a build/ERC20Token.abi -o . -p io.lightcone.ethereum.contract
web3j solidity generate -b build/WETH9.bin -a build/WETH9.abi -o . -p io.lightcone.ethereum.contract
