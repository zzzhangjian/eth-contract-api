package org.adridadou.ethereum.smartcontract;

import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.facade.Ethereum;
import org.ethereum.solidity.compiler.CompilationResult;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.blockchain.SolidityContract;
import org.ethereum.util.blockchain.SolidityStorage;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

/**
 * Created by davidroon on 20.04.16.
 * This code is released under Apache 2 license
 */
public class SolidityContractImpl implements SolidityContract {
    private byte[] address;
    private CompilationResult.ContractMetadata compiled;
    private CallTransaction.Contract contract;
    private final Ethereum ethereum;
    private final ECKey sender;

    public SolidityContractImpl(CompilationResult.ContractMetadata result, Ethereum ethereum, ECKey sender) {
        compiled = result;
        contract = new CallTransaction.Contract(compiled.abi);
        this.ethereum = ethereum;
        this.sender = sender;
    }

    public void setAddress(byte[] address) {
        this.address = address;
    }

    private BlockchainImpl getBlockchain() {
        return (BlockchainImpl) ethereum.getBlockchain();
    }

    private Repository getRepository() {
        return getBlockchain().getRepository();
    }

    @Override
    public byte[] getAddress() {
        if (address == null) {
            throw new RuntimeException("Contract address will be assigned only after block inclusion. Call createBlock() first.");
        }
        return address;
    }

    @Override
    public Object[] callFunction(String functionName, Object... args) {
        return callFunction(0, functionName, args);
    }

    @Override
    public Object[] callFunction(long value, String functionName, Object... args) {
        CallTransaction.Contract contract = new CallTransaction.Contract(compiled.abi);
        CallTransaction.Function inc = contract.getByName(functionName);
        byte[] functionCallBytes = inc.encode(args);
        sendTx(address, functionCallBytes);

        return null;
    }

    @Override
    public Object[] callConstFunction(String functionName, Object... args) {
        return callConstFunction(getBlockchain().getBestBlock(), functionName, args);
    }

    @Override
    public Object[] callConstFunction(Block callBlock, String functionName, Object... args) {

        Transaction tx = CallTransaction.createCallTransaction(0, 0, 100000000000000L,
                Hex.toHexString(getAddress()), 0, contract.getByName(functionName), args);
        tx.sign(new byte[32]);

        Repository repository = getRepository().getSnapshotTo(callBlock.getStateRoot()).startTracking();

        try {
            TransactionExecutor executor = new TransactionExecutor
                    (tx, callBlock.getCoinbase(), repository, getBlockchain().getBlockStore(),
                            getBlockchain().getProgramInvokeFactory(), callBlock)
                    .setLocalCall(true);

            executor.init();
            executor.execute();
            executor.go();
            executor.finalization();

            return contract.getByName(functionName).decodeResult(executor.getResult().getHReturn());
        } finally {
            repository.rollback();
        }
    }

    @Override
    public SolidityStorage getStorage() {
        return new SolidityStorageImpl(getAddress(), getBlockchain());
    }

    @Override
    public String getABI() {
        return compiled.abi;
    }

    @Override
    public String getBinary() {
        return compiled.bin;
    }

    @Override
    public void call(byte[] callData) {
        // for this we need cleaner separation of EasyBlockchain to
        // Abstract and Solidity specific
        throw new UnsupportedOperationException();
    }

    private void sendTx(byte[] receiveAddress, byte[] data) {
        BigInteger nonce = getRepository().getNonce(sender.getAddress());
        Transaction tx = new Transaction(
                ByteUtil.bigIntegerToBytes(nonce),
                ByteUtil.longToBytesNoLeadZeroes(ethereum.getGasPrice()),
                ByteUtil.longToBytesNoLeadZeroes(3_000_000),
                receiveAddress,
                ByteUtil.longToBytesNoLeadZeroes(1),
                data);
        tx.sign(sender.getPrivKeyBytes());
        ethereum.submitTransaction(tx);
    }
}
