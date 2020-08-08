package Lottery

import fr.acinq.bitcoin.Crypto.PrivateKey
import fr.acinq.bitcoin._
import scodec.bits.ByteVector

object TimedComm {

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

    println(Bech32.encodeWitnessAddress("bcrt", 0, Crypto.hash160(a_pub.value)))

    // funds transaction generated on regtest with sendtoaddress bcrt1qpvya0fx4a3rny37jt8twqe4kt95qwcvagvn7ed
    val fundsA = Transaction.read("0200000000010138d27aecef70c1d75ed12c91c21b9d0ed4ca183e292b3ceb5bb9ecedc90807a60000000000fdffffff0200ca9a3b000000001600140b09d7a4d5ec473247d259d6e066b6596807619d73276bee00000000160014d50620940f469e42164daded4cc14b92be2c02de024730440220076a234d47b5bf83764e4c7569d567d5d79bb37ce0449164d8f711a951b512880220599ace3b2f415932aa79933ba6a478f39cce1782ea1b417def58a3b7a792cb13012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a00000000")

    val t_commit = {
        val tmp: Transaction = Transaction(version = 2,
          txIn = TxIn(OutPoint(fundsA.hash, 0), sequence = 0xffffffffL, signatureScript = ByteVector.empty, witness = ScriptWitness.empty) :: Nil,
          txOut = TxOut(9.999 btc, Script.pay2wsh(Script.parse(ByteVector.fromValidHex("766b7c6ba82073475cb40a568e8da8a045ced110137e159f890ac4da883b6b17dc651b3a804987636c766b2103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93aac6700686351670480bb1d5bb1756c766b21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7ac68")))) :: Nil,
          lockTime = 0
        )
        // mind this: the pubkey script used for signing is not the prevout pubscript (which is just a push
        // of the pubkey hash), but the actual script that is evaluated by the script engine, in this case a PAY2PKH script
          val pubKeyScript = Script.pay2pkh(a_pub)
          val sig = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, fundsA.txOut(0).amount, SigVersion.SIGVERSION_WITNESS_V0, a_priv)
          val witness = ScriptWitness(Seq(sig, a_pub.value))
          tmp.updateWitness(0, witness)
      }
      //check that the transaction is spendable
      println(t_commit)
      Transaction.correctlySpends(t_commit, Seq(fundsA), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)

    val t_reveal = {
      val tmp: Transaction = Transaction(version = 2,
        txIn = TxIn(OutPoint(t_commit.hash, 0), sequence = 0xffffffffL, signatureScript = ByteVector.empty, witness = ScriptWitness.empty) :: Nil,
        txOut = TxOut(9.998 btc, Script.pay2wpkh(a_pub)) :: Nil,
        lockTime = 0L
      )
      // mind this: the pubkey script used for signing is not the prevout pubscript (which is just a push
      // of the pubkey hash), but the actual script that is evaluated by the script engine, in this case a PAY2PKH script
      val pubKeyScript = ByteVector.fromValidHex("766b7c6ba82073475cb40a568e8da8a045ced110137e159f890ac4da883b6b17dc651b3a804987636c766b2103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93aac6700686351670480bb1d5bb1756c766b21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7ac68")
      val sig = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, t_commit.txOut(0).amount, SigVersion.SIGVERSION_WITNESS_V0, a_priv)
      println(sig.toString())
      val sec = ByteVector.fromValidHex("3432")
      val witness = ScriptWitness(Seq(ByteVector.empty, ByteVector.empty, pubKeyScript))
      tmp.updateWitness(0, witness)
    }
    //check that the transaction is spendable
    println(t_reveal)
    //Transaction.correctlySpends(t_reveal, Seq(fundsA, t_commit), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)

    val t_timeout = {
      val tmp: Transaction = Transaction(version = 2,
        txIn = TxIn(OutPoint(t_commit.hash, 0), sequence = 0xfffffffaL, signatureScript = ByteVector.empty, witness = ScriptWitness.empty) :: Nil,
        txOut = TxOut(9.998 btc, Script.pay2wpkh(a_pub)) :: Nil,
        lockTime = 1528675200L
      )
      // mind this: the pubkey script used for signing is not the prevout pubscript (which is just a push
      // of the pubkey hash), but the actual script that is evaluated by the script engine, in this case a PAY2PKH script
      val pubKeyScript = ByteVector.fromValidHex("766b7c6ba82073475cb40a568e8da8a045ced110137e159f890ac4da883b6b17dc651b3a804987636c766b2103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93aac6700686351670480bb1d5bb1756c766b21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7ac68")
      val sig = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, t_commit.txOut(0).amount, SigVersion.SIGVERSION_WITNESS_V0, b_priv)
      println(sig.toString())
      val sec = ByteVector.fromValidHex("00")
      val witness = ScriptWitness(Seq(sig, sec, pubKeyScript))
      tmp.updateWitness(0, witness)
    }
    //check that the transaction is spendable
    println(t_reveal)
    Transaction.correctlySpends(t_reveal, Seq(fundsA, t_commit), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)


  }
}

