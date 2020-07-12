package Tests_Decentralized

import fr.acinq.bitcoin
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
    val a1_addr = "bcrt1qpvya0fx4a3rny37jt8twqe4kt95qwcvagvn7ed"
    val a2_addr = "bcrt1q6hhz23me8gqumprwzdrm4yrcy9yntp7l53nzt6"
    val a3_addr = "bcrt1qjmdu5x7tujksjr7yt4mp7d0ay0a3jlutlnhe0s"
    val a4_addr = "bcrt1q39me5uk3lmvyar66cytvncqkdgpawj3ld7xg5n"
    val curator_addr = "bcrt1qp2g8a0ele0ywv9tc9ywutv58j0prhqznxvltn0"

    //private keys
    val a1_priv = PrivateKey.fromBase58("cVbFzgZSpnuKvNT5Z3DofF9dV4Dr1zFQJw9apGZDVaG73ULqM7XS", Base58.Prefix.SecretKeyTestnet)._1
    val a2_priv = PrivateKey.fromBase58("cPU3AmQFsBxvrBgTWc1j3pS6T7m4bYWMFQyPnR9Qp3o3UTCBwspZ", Base58.Prefix.SecretKeyTestnet)._1
    val a3_priv = PrivateKey.fromBase58("cSmRqDGe8UoQy4jmhPU7c88iCJ6V7uV8EPJ9b194bBX5JqH2YQN5", Base58.Prefix.SecretKeyTestnet)._1
    val a4_priv = PrivateKey.fromBase58("cTnoNcfq1S3xYuAZnGWictR1FRNxsuVChUK9iHXpVa3EEg8Y5YxG", Base58.Prefix.SecretKeyTestnet)._1
    val curator_priv = PrivateKey.fromBase58("cQAEMfAQwbVDSUDT3snYu9QVfbdBTVMrm36zoArizBkAaPYTtLdH", Base58.Prefix.SecretKeyTestnet)._1

    //public keys
    val a1_pub = a1_priv.publicKey
    val a2_pub = a2_priv.publicKey
    val a3_pub = a3_priv.publicKey
    val a4_pub = a4_priv.publicKey
    val curator_pub = curator_priv.publicKey

    // funds transaction generated on regtest with sendtoaddress bcrt1qpvya0fx4a3rny37jt8twqe4kt95qwcvagvn7ed
    val funds1 = Transaction.read("020000000001011753560d944608753b764ad86fd1e54c35343d60292abf2aff46446803073ee80000000000fdffffff0200e1f505000000001600140b09d7a4d5ec473247d259d6e066b6596807619d7310102401000000160014751ddd5bc88bc6eb1b84e4e488da1200eea3cfd20247304402207b790e7d6678270dc5e9b4a9d5d4858ff17e96ded803f266ab25b4d354b0face022058554af43bc6748ecc4e83f79e8d55a586ae480983d1b2d00c25d1b356f593d3012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93ac1000000")
    val funds2 = Transaction.read("0200000000010122c078dd11b0796eb52ecf3e829a19eb495c885953955c38c8c417f1e169a1300000000000fdffffff0200e1f50500000000160014d5ee2547793a01cd846e1347ba907821493587df731010240100000016001480d3f7add00a1c5211e440f767d037032d67fa8f0247304402201c4c4bf953b819b66fe124ca62d113e8feb6df73c47a4cb33b2e38c50774d0d602205c1f45a481b43e7114bccc68b7c011abe85f6a54fde02bcea63f9cf22a8e6c1f012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93ac1000000")
    val funds3 = Transaction.read("02000000000101dd398445a4948be3d8368d8f22300199d0b48593693d5a0c415a2f9bc48655cc0000000000fdffffff0200e1f5050000000016001496dbca1bcbe4ad090fc45d761f35fd23fb197f8b73101024010000001600140bba74cab2077e757915a34dbeb511aff894241902473044022046ffd5b06e35eacdc4d4456d2ddfeadca4d20306db5acf30dfe3a148c13e6b7802201457d2b4679ee41d56de0b5d1e4be7c70dee0bb566e594c642954978f4e5462b012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93ac1000000")
    val funds4 = Transaction.read("0200000000010185a9959218f7cb4d50fb3927e2d0a1171d6ac32d62b386f98b353335fb56d2580000000000fdffffff0200e1f5050000000016001489779a72d1fed84e8f5ac116c9e0166a03d74a3f7310102401000000160014d87d4306c4809364eca8a9fbace6138d1d3dc16202473044022062a401c5a8a800e83a25c292f6d04291f80bf98d29f2ea45dd7103272d7efee20220549d7ecf7ee3381302377557c8f334501ad70cd9f388e492260a4811f84e9e57012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93ac1000000")
    val tv = {
      val tmp: Transaction = Transaction(version = 2,
        txIn = List(TxIn(OutPoint(funds1.hash, 0), sequence = 0xffffffffL, signatureScript = ByteVector.empty, witness = ScriptWitness.empty),
                TxIn(OutPoint(funds2.hash, 0), sequence = 0xffffffffL, signatureScript = ByteVector.empty, witness = ScriptWitness.empty),
                TxIn(OutPoint(funds3.hash, 0), sequence = 0xffffffffL, signatureScript = ByteVector.empty, witness = ScriptWitness.empty),
                TxIn(OutPoint(funds4.hash, 0), sequence = 0xffffffffL, signatureScript = ByteVector.empty, witness = ScriptWitness.empty)
        ),
        txOut = TxOut(3.99 btc, Script.pay2wpkh(curator_pub)) :: Nil,
        lockTime = 0
      )
      val pubKeyScript1 = Script.pay2pkh(a1_pub)
      val pubKeyScript2 = Script.pay2pkh(a2_pub)
      val pubKeyScript3 = Script.pay2pkh(a3_pub)
      val pubKeyScript4 = Script.pay2pkh(a4_pub)
      val sig1 = Transaction.signInput(tmp, 0, pubKeyScript1, SIGHASH_ALL, funds1.txOut(0).amount, SigVersion.SIGVERSION_WITNESS_V0, a1_priv)
      val sig2 = Transaction.signInput(tmp, 1, pubKeyScript2, SIGHASH_ALL, funds2.txOut(0).amount, SigVersion.SIGVERSION_WITNESS_V0, a2_priv)
      val sig3 = Transaction.signInput(tmp, 2, pubKeyScript3, SIGHASH_ALL, funds3.txOut(0).amount, SigVersion.SIGVERSION_WITNESS_V0, a3_priv)
      val sig4 = Transaction.signInput(tmp, 3, pubKeyScript4, SIGHASH_ALL, funds4.txOut(0).amount, SigVersion.SIGVERSION_WITNESS_V0, a4_priv)
      val witness1 = ScriptWitness(Seq(ByteVector.empty, a1_pub.value))
      val witness2 = ScriptWitness(Seq(ByteVector.empty, a2_pub.value))
      val witness3 = ScriptWitness(Seq(ByteVector.empty, a3_pub.value))
      val witness4 = ScriptWitness(Seq(ByteVector.empty, a4_pub.value))
      tmp.updateWitnesses(Seq(witness1, witness2, witness3, witness4))
    }
    //check that the transaction is spendable
    println(tv)
    Transaction.correctlySpends(tv, Seq(funds1, funds2, funds3, funds4), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)
  }

}
