package com.RobinNotBad.BiliClient.util;

/** Process-local unread truth. A page read or account change invalidates earlier requests. */
public final class MessageUnreadState {
    public static final class Check {
        private final long account, revision, sequence;
        private Check(long account,long revision,long sequence) {
            this.account=account;this.revision=revision;this.sequence=sequence;
        }
    }
    private long account, revision, sequence;
    private int interactions, privateMessages;
    private boolean fresh;

    public synchronized void reset(long account) {
        this.account=account;revision++;sequence++;interactions=privateMessages=0;fresh=false;
    }
    public synchronized Check begin(long account) {
        if(this.account!=account)reset(account);
        return account>0?new Check(account,revision,++sequence):null;
    }
    public synchronized boolean complete(Check check,long currentAccount,int count) {
        return complete(check,currentAccount,count,0);
    }
    public synchronized boolean complete(Check check,long currentAccount,Integer interactions,Integer privateMessages) {
        if(!valid(check,currentAccount))return false;
        revision++;
        if(interactions!=null)this.interactions=Math.max(0,interactions);
        if(privateMessages!=null)this.privateMessages=Math.max(0,privateMessages);
        fresh=fresh||interactions!=null||privateMessages!=null;
        return true;
    }
    public synchronized boolean fail(Check check,long currentAccount) {
        return complete(check,currentAccount,null,null);
    }
    private boolean valid(Check check,long currentAccount) {
        return check!=null&&currentAccount>0&&check.account==currentAccount&&account==currentAccount
                &&check.revision==revision&&check.sequence==sequence;
    }
    public synchronized int count(long currentAccount) {
        return currentAccount>0&&currentAccount==account&&fresh
                ?(int)Math.min(Integer.MAX_VALUE,(long)interactions+privateMessages):0;
    }
}
