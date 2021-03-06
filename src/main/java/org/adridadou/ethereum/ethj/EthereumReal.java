package org.adridadou.ethereum.ethj;

import org.adridadou.ethereum.EthereumBackend;
import org.adridadou.ethereum.event.EthereumEventHandler;
import org.adridadou.ethereum.values.*;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.facade.Ethereum;

import java.math.BigInteger;

import static org.adridadou.ethereum.values.EthValue.wei;

/**
 * Created by davidroon on 20.01.17.
 * This code is released under Apache 2 license
 */
public class EthereumReal implements EthereumBackend {
    private final Ethereum ethereum;
    private final LocalExecutionService localExecutionService;

    public EthereumReal(Ethereum ethereum) {
        this.ethereum = ethereum;
        this.localExecutionService = new LocalExecutionService((BlockchainImpl)ethereum.getBlockchain());
    }

    @Override
    public BigInteger getGasPrice() {
        return BigInteger.valueOf(ethereum.getGasPrice());
    }

    @Override
    public EthValue getBalance(EthAddress address) {
        return wei(getRepository().getBalance(address.address));
    }

    @Override
    public boolean addressExists(EthAddress address) {
        return getRepository().isExist(address.address);
    }

    @Override
    public EthHash submit(EthAccount account, EthAddress address, EthValue value, EthData data, BigInteger nonce, BigInteger gasLimit) {
        Transaction tx = ethereum.createTransaction(nonce, getGasPrice(), gasLimit, address.address, value.inWei(), data.data);
        tx.sign(getKey(account));
        ethereum.submitTransaction(tx);

        return EthHash.of(tx.getHash());
    }

    private ECKey getKey(EthAccount account) {
        return ECKey.fromPrivate(account.getPrivateKey());
    }

    @Override
    public BigInteger getNonce(EthAddress currentAddress) {
        return getRepository().getNonce(currentAddress.address);
    }

    @Override
    public long getCurrentBlockNumber() {
        return getBlockchain().getBestBlock().getNumber();
    }

    @Override
    public SmartContractByteCode getCode(EthAddress address) {
        return SmartContractByteCode.of(getRepository().getCode(address.address));
    }

    @Override
    public EthData constantCall(EthAccount account, EthAddress address, EthValue value, EthData data) {
        return localExecutionService.executeLocally(account, address, value, data);
    }

    @Override
    public void register(EthereumEventHandler eventHandler) {
        ethereum.addListener(new EthJEventListener(eventHandler));
    }

    public BlockchainImpl getBlockchain() {
        return (BlockchainImpl) ethereum.getBlockchain();
    }

    private Repository getRepository() {
        return getBlockchain().getRepository();
    }

    @Override
    public BigInteger estimateGas(EthAccount account, EthAddress address, EthValue value, EthData data) {
        return localExecutionService.estimateGas(account, address, value, data);
    }

}
