package Tests_Decentralized

import fr.acinq.bitcoin.Crypto.{PrivateKey, PublicKey}
import fr.acinq.bitcoin._
import scodec.bits.ByteVector
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient

object CreateTransactions {

  def main(args: Array[String]): Unit = {
    createTransaction()
  }

  def createTransaction() : Unit = {
    //regtest addresses of the partecipants
    val alice_addr = "bcrt1qpvya0fx4a3rny37jt8twqe4kt95qwcvagvn7ed"
    val bob_addr = "bcrt1q6hhz23me8gqumprwzdrm4yrcy9yntp7l53nzt6"
    val oracle_addr = "bcrt1qp2g8a0ele0ywv9tc9ywutv58j0prhqznxvltn0"

    //private keys
    val a_priv = PrivateKey.fromBase58("cVbFzgZSpnuKvNT5Z3DofF9dV4Dr1zFQJw9apGZDVaG73ULqM7XS", Base58.Prefix.SecretKeyTestnet)._1
    val b_priv = PrivateKey.fromBase58("cPU3AmQFsBxvrBgTWc1j3pS6T7m4bYWMFQyPnR9Qp3o3UTCBwspZ", Base58.Prefix.SecretKeyTestnet)._1
    val o_priv = PrivateKey.fromBase58("cQAEMfAQwbVDSUDT3snYu9QVfbdBTVMrm36zoArizBkAaPYTtLdH", Base58.Prefix.SecretKeyTestnet)._1

    //public keys
    val a_pub = a_priv.publicKey
    val b_pub = b_priv.publicKey
    val o_pub = o_priv.publicKey

    println(a_pub)
    println(b_pub)
    println(o_pub)

    println(Bech32.encodeWitnessAddress("bcrt",0,Crypto.hash160(a_pub.value)))

    // funds transaction generated on regtest with sendtoaddress bcrt1qpvya0fx4a3rny37jt8twqe4kt95qwcvagvn7ed
    val funds = Transaction.read("02000000000101d09468bdabc2281b8d869db936392659bc2baf1438e43558c928955535c871ab0000000000fdffffff0200e1f505000000001600140b09d7a4d5ec473247d259d6e066b6596807619de6c89a3b00000000160014850d4db386a040deddd7e12e9775ffedada11a970247304402201d149721fab0237944ceeaab4f25d7a4a7a643e435fd0030a054800901a5993602207fcff2423e9ae4b633258aee769ed598dab57848cf0f4539865c2a5ab84421af012103f67805049b2aa90e5754faac9f7ff6c53a369f068ff294e1d9d11de16e5a758200000000")

    /*
      transaction T {
        input = A_funds: sig(kA)
        output = 1 BTC: fun(sigB, sigO). versig(Bob.kBpub, Oracle.kOpub; sigB, sigO)
      }
     */
    val t = {
      // our script is a 2-of-2 multisig script
      val redeemScript = Script.createMultiSigMofN(2, Seq(b_pub, o_pub))
      val tmp: Transaction = Transaction(version = 2,
        txIn = TxIn(OutPoint(funds.hash, 0), sequence = 0xffffffffL, signatureScript = ByteVector.empty, witness = ScriptWitness.empty) :: Nil,
        txOut = TxOut(0.99 btc, Script.pay2wsh(redeemScript)) :: Nil,
        lockTime = 0
      )
      // mind this: the pubkey script used for signing is not the prevout pubscript (which is just a push
      // of the pubkey hash), but the actual script that is evaluated by the script engine, in this case a PAY2PKH script
        val pubKeyScript = Script.pay2pkh(a_pub)
        val sig = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, funds.txOut(0).amount, SigVersion.SIGVERSION_WITNESS_V0, a_priv)
        val witness = ScriptWitness(Seq(sig, a_pub.value))
        tmp.updateWitness(0, witness)
    }
    //check that the transaction is spendable
    println(t)
    Transaction.correctlySpends(t, Seq(funds), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)

    /*
      transaction T1(sigO) {
        input = Alice.T: sig(kB) sigO
        output = 1 BTC: fun(x). versig(kB; x)
     }
     */
    val t1 = {
      val tmp: Transaction = Transaction(version = 2,
        txIn = TxIn(OutPoint(t.hash, 0), sequence = 0xffffffffL, signatureScript = ByteVector.empty) :: Nil,
        txOut = TxOut(0.98 btc, Script.pay2wpkh(b_pub)) :: Nil,
        lockTime = 0
      )
      //sign the multisig witness input
      val pubKeyScript = Script.write(Script.createMultiSigMofN(2, Seq(b_pub, o_pub)))
      val sig2 = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, 0.99 btc, SigVersion.SIGVERSION_WITNESS_V0, b_priv)
      val sig3 = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, 0.99 btc, SigVersion.SIGVERSION_WITNESS_V0, o_priv)
      val witness = ScriptWitness(Seq(ByteVector.empty, sig2, sig3, pubKeyScript))
      tmp.updateWitness(0, witness)
    }
    //check that the transaction is spendable
    println(t1)
    Transaction.correctlySpends(t1, Seq(funds, t), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)

    //dump the serialized transactions
    println("t "+t.toString())
    println("t1 "+t1.toString())
  }

}
