package org.peridoll.mcodyssey.eco;

import org.peridoll.mcodyssey.core.Database;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class Economy {
    public static final long INITIAL_BALANCE = 1000;
    private final Database db;
    public Economy(Database db) { this.db=db; }
    public CompletableFuture<Void> initialize() { return db.submit(c -> {
        try(var s=c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS eco_accounts(uuid TEXT PRIMARY KEY REFERENCES residents(uuid),balance INTEGER NOT NULL CHECK(balance>=0))");
            s.execute("CREATE TABLE IF NOT EXISTS eco_transactions(id INTEGER PRIMARY KEY AUTOINCREMENT,sender TEXT NOT NULL REFERENCES eco_accounts(uuid),recipient TEXT NOT NULL REFERENCES eco_accounts(uuid),amount INTEGER NOT NULL CHECK(amount>0),created_at INTEGER NOT NULL)");
        } return null;
    }); }
    private void ensure(Connection c, UUID id) throws SQLException {
        try(var s=c.prepareStatement("INSERT OR IGNORE INTO eco_accounts(uuid,balance) VALUES(?,?)")) {
            s.setString(1,id.toString()); s.setLong(2,INITIAL_BALANCE); s.executeUpdate();
        }
    }
    public CompletableFuture<Long> balance(UUID id) { return db.transaction(c -> {
        ensure(c,id); try(var s=c.prepareStatement("SELECT balance FROM eco_accounts WHERE uuid=?")) {
            s.setString(1,id.toString()); try(var rs=s.executeQuery()) { if(!rs.next()) throw new SQLException("Missing account"); return rs.getLong(1); }
        }
    }); }
    public CompletableFuture<Void> pay(UUID from,UUID to,long amount) {
        if(amount<=0 || from.equals(to)) return CompletableFuture.failedFuture(new IllegalArgumentException("送金額は1以上、相手は自分以外にしてください"));
        return db.transaction(c -> {
            ensure(c,from); ensure(c,to);
            try(var s=c.prepareStatement("UPDATE eco_accounts SET balance=balance-? WHERE uuid=? AND balance>=?")) {
                s.setLong(1,amount); s.setString(2,from.toString()); s.setLong(3,amount);
                if(s.executeUpdate()!=1) throw new IllegalArgumentException("残高が不足しています");
            }
            try(var s=c.prepareStatement("UPDATE eco_accounts SET balance=balance+? WHERE uuid=? AND balance<=?")) {
                s.setLong(1,amount); s.setString(2,to.toString()); s.setLong(3,Long.MAX_VALUE-amount);
                if(s.executeUpdate()!=1) throw new IllegalArgumentException("相手の残高上限を超えます");
            }
            try(var s=c.prepareStatement("INSERT INTO eco_transactions(sender,recipient,amount,created_at) VALUES(?,?,?,?)")) {
                s.setString(1,from.toString()); s.setString(2,to.toString()); s.setLong(3,amount); s.setLong(4,System.currentTimeMillis()); s.executeUpdate();
            } return null;
        });
    }
    public CompletableFuture<List<String>> history(UUID id) { return db.submit(c -> {
        List<String> lines=new ArrayList<>();
        try(var s=c.prepareStatement("SELECT t.id,t.sender,t.amount,r.name FROM eco_transactions t JOIN residents r ON r.uuid=CASE WHEN t.sender=? THEN t.recipient ELSE t.sender END WHERE t.sender=? OR t.recipient=? ORDER BY t.id DESC LIMIT 10")) {
            for(int i=1;i<=3;i++) s.setString(i,id.toString());
            try(var rs=s.executeQuery()) { while(rs.next()) lines.add("#"+rs.getLong(1)+" "+(rs.getString(2).equals(id.toString())?"送金 → ":"受取 ← ")+rs.getString(4)+" : "+rs.getLong(3)+" MC"); }
        } return lines;
    }); }
}
