package com.techelevator.tenmo.dao;

import com.techelevator.tenmo.exception.DaoException;
import com.techelevator.tenmo.model.Account;
import com.techelevator.tenmo.model.Transfer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class JdbcTransferDao implements TransferDao {

    private final JdbcTemplate jdbcTemplate;
    private AccountDao accountDao;

    private UserDao userDao;


    public JdbcTransferDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.accountDao = new JdbcAccountDao(jdbcTemplate);
        this.userDao = new JdbcUserDao(jdbcTemplate);
    }

    public Transfer getTransferById(int transfer_id){
        Transfer transfer = null;
        String sql = "SELECT transfer_id, transfer_type_id, transfer_status_id, account_from, account_to, amount " +
                "FROM transfer " +
                "WHERE transfer_id = ?;" ;
        SqlRowSet results = jdbcTemplate.queryForRowSet(sql, transfer_id);
        if(results.next()){
            transfer = mapRowToTransfer(results);
        }
        return transfer;
    }

    @Override
    public List<Transfer> getTransfersByUserId(int user_id) {
        List<Transfer> transfers = new ArrayList<>();
        String sql = "SELECT transfer_id, transfer_type_id, transfer_status_id, account_from, account_to, amount, t_from.username AS username_from, t_to.username AS username_to, t_from.user_id AS user_id_from, t_to.user_id AS user_id_to " +
                "FROM transfer " +
                "JOIN account a_from ON a_from.account_id = account_from\n" +
                "JOIN account a_to ON a_to.account_id = account_to\n" +
                "JOIN tenmo_user t_from ON a_from.user_id = t_from.user_id\n" +
                "JOIN tenmo_user t_to ON a_to.user_id = t_to.user_id\n" +
                "WHERE t_from.user_id = ?";

        SqlRowSet results = jdbcTemplate.queryForRowSet(sql, user_id);
        while(results.next()){
            transfers.add(mapRowToTransfer(results));
        }
        return transfers;
    }

    @Override
    public Transfer sendTransfers(Transfer createdtransfer) {
     BigDecimal valueOne = accountDao.getBalanceById(createdtransfer.getUser_id_from()).getBalance();
            boolean isValid = false;
            if(createdtransfer.getUser_id_from() != createdtransfer.getUser_id_to() && createdtransfer.getAmount().compareTo(valueOne) <= 0){
                isValid = true;

            }
            else{
                throw new DaoException("This is not valid you stupid !");
            }
        Transfer newTransfer = null;
        String sql = "INSERT INTO transfer (transfer_type_id, transfer_status_id, account_from, account_to, amount) \n" +
                "                VALUES (2, 2, (SELECT account_id FROM account WHERE user_id = ?), (SELECT account_id FROM account WHERE user_id = ?), ?) RETURNING transfer_id;";


            String sql1 = "UPDATE account SET balance = balance - ? WHERE user_id =?";

            String sql2 = "UPDATE account SET balance = balance + ? WHERE user_id =?";

            BigDecimal zero = new BigDecimal(0);
            BigDecimal sendingBal = accountDao.getbalance(userDao.getUserById(createdtransfer.getUser_id_from()));
            if(createdtransfer.getUser_id_from() != createdtransfer.getUser_id_to()){
               if(createdtransfer.getAmount().compareTo(zero) == 1 && createdtransfer.getAmount().compareTo(sendingBal) <= 0 && createdtransfer.getAmount().compareTo(BigDecimal.ZERO) > 0){
                   try {
                       int newTransferId = jdbcTemplate.queryForObject(sql, int.class, createdtransfer.getUser_id_from(), createdtransfer.getUser_id_to(), createdtransfer.getAmount());
                       jdbcTemplate.update(sql1, createdtransfer.getAmount(), createdtransfer.getUser_id_from());
                       jdbcTemplate.update(sql2, createdtransfer.getAmount(), createdtransfer.getUser_id_to());
                       newTransfer = getTransferById(newTransferId);
                   }
                   catch (CannotGetJdbcConnectionException e) {
                       throw new DaoException("Unable to connect to server or database", e);
                   } catch (DataIntegrityViolationException e) {
                       throw new DaoException("Data integrity violation", e);
                   }
                   return newTransfer;

               }
               else{
                   throw new DaoException("Transfer Amount Must Be Less Than The Account Balance!");
               }
            }else{
                throw new DaoException("Cannot Transfer Money to Yourself, Nice TRY!!!!");
            }

    }

    @Override
    public Transfer updateTransferStatus(Transfer updateTransfer) {
        Transfer updateTransfers = null;
        String sql = "UPDATE transfer SET transfer_type_id = ?, transfer_status_id = ?, account = ? " +
                    "WHERE transfer_id = ?;";
        try{
            int updatedTransfer = jdbcTemplate.update(sql, updateTransfer.getTransfer_type_id(), updateTransfer.getTransfer_status_id(), updateTransfer.getAmount());

        }catch (CannotGetJdbcConnectionException e) {
            throw new DaoException("Unable to connect to server or database", e);
        } catch (DataIntegrityViolationException e) {
            throw new DaoException("Data integrity violation", e);
        }
        return updateTransfers;
    }
//    public void increaseBalance

    private Transfer mapRowToTransfer(SqlRowSet rowSet){
        Transfer transfer = new Transfer();
        transfer.setTransfer_id(rowSet.getInt("transfer_id"));
        transfer.setTransfer_type_id(rowSet.getInt("transfer_type_id"));
        transfer.setTransfer_status_id(rowSet.getInt("transfer_status_id"));
        transfer.setUser_id_from(rowSet.getInt("user_id_from"));
        transfer.setUser_id_to(rowSet.getInt("user_id_to"));
        transfer.setAmount(rowSet.getBigDecimal("amount"));
        transfer.setUsernameFrom(rowSet.getString("username_from"));
        transfer.setUsernameTo(rowSet.getString("username_to"));
        return transfer;
    }
}
