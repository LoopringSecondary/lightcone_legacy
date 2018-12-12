package org.loopring.lightcone.lib

object MarketHashProvider {

  def convert2BigInt(address1: String, address2: String) : BigInt = {
    BigInt(address1.substring(2).trim, 16) ^ BigInt(address2.substring(2).trim, 16)
  }
  def convert2HexString(address1: String, address2: String) : BigInt = {
    BigInt(address1.substring(2).trim, 16) ^ BigInt(address2.substring(2).trim, 16)
  }
}
