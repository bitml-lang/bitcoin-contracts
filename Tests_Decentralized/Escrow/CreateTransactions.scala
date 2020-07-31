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
    val funds = Transaction.read("0200000000010126d018cd48f3abca4e73da3b53c16d49e461428e2a2c6bde60518c55eccf9df30000000000fdffffff0200e1f505000000001600140b09d7a4d5ec473247d259d6e066b6596807619d7310102401000000160014d5dee1ef6d75d8f014be7a415bc31ce8e8901aea02473044022053fa1b972ae7f0f4358c0df624f99fad8d858de06adcebad5e0cdab00a344ab902206af154ad6492ff67d592def37567605d30913518c0eb9f34195c003f4a814eb9012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a00000000")

    val t = {
      // our script is a 2-of-2 multisig script
      val redeemScript = Script.createMultiSigMofN(2, Seq(a_pub, b_pub, o_pub))
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

    val t1_bob = {
      val tmp: Transaction = Transaction(version = 2,
        txIn = TxIn(OutPoint(t.hash, 0), sequence = 0xffffffffL, signatureScript = ByteVector.empty) :: Nil,
        txOut = TxOut(0.98 btc, Script.pay2wpkh(b_pub)) :: Nil,
        lockTime = 0
      )
      //sign the multisig witness input
      val pubKeyScript = Script.write(Script.createMultiSigMofN(2, Seq(a_pub, b_pub, o_pub)))
      val sig2 = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, 0.99 btc, SigVersion.SIGVERSION_WITNESS_V0, a_priv)
      val sig3 = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, 0.99 btc, SigVersion.SIGVERSION_WITNESS_V0, b_priv)
      val witness = ScriptWitness(Seq(ByteVector.empty, ByteVector.empty, ByteVector.empty, pubKeyScript))
      tmp.updateWitness(0, witness)
    }
    //check that the transaction is spendable
    println(t1_bob)
    //Transaction.correctlySpends(t1_bob, Seq(funds, t), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)

    val t1_alice = {
      val tmp: Transaction = Transaction(version = 2,
        txIn = TxIn(OutPoint(t.hash, 0), sequence = 0xffffffffL, signatureScript = ByteVector.empty) :: Nil,
        txOut = TxOut(0.98 btc, Script.pay2wpkh(a_pub)) :: Nil,
        lockTime = 0
      )
      //sign the multisig witness input
      val pubKeyScript = Script.write(Script.createMultiSigMofN(2, Seq(a_pub, b_pub, o_pub)))
      val sig2 = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, 0.99 btc, SigVersion.SIGVERSION_WITNESS_V0, a_priv)
      val sig3 = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, 0.99 btc, SigVersion.SIGVERSION_WITNESS_V0, b_priv)
      val witness = ScriptWitness(Seq(ByteVector.empty, ByteVector.empty, ByteVector.empty, pubKeyScript))
      tmp.updateWitness(0, witness)
    }
    //check that the transaction is spendable
    println(t1_alice)
    //Transaction.correctlySpends(t1_alice, Seq(funds, t), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)

    val t1_split = {
      val tmp: Transaction = Transaction(version = 2,
        txIn = TxIn(OutPoint(t.hash, 0), sequence = 0xffffffffL, signatureScript = ByteVector.empty) :: Nil,
        txOut = List(TxOut(0.49 btc, Script.pay2wpkh(a_pub)),
          TxOut(0.49 btc, Script.pay2wpkh(b_pub))),
        lockTime = 0
      )
      //sign the multisig witness input
      val pubKeyScript = Script.write(Script.createMultiSigMofN(2, Seq(a_pub, b_pub, o_pub)))
      val sig2 = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, 0.99 btc, SigVersion.SIGVERSION_WITNESS_V0, a_priv)
      val sig3 = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, 0.99 btc, SigVersion.SIGVERSION_WITNESS_V0, o_priv)
      val witness = ScriptWitness(Seq(ByteVector.empty, sig2, sig3, pubKeyScript))
      tmp.updateWitness(0, witness)
    }
    //check that the transaction is spendable
    Transaction.correctlySpends(t1_split, Seq(t), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)
    //dump the serialized transactions
    println("t "+t.toString())
    println("t1_bob "+t1_bob.toString())
    println("t1_alice "+t1_alice.toString())
    println("t1_split "+t1_split.toString())
  }

}