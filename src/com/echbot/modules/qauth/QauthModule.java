package com.echbot.modules.qauth;

import com.echbot.UserModule;
import com.echbot.UserModuleInterface;
import com.echbot.messages.in.ChatMessageIn;
import com.echbot.messages.out.ChatMessageOut;
import com.echbot.modules.auth.AuthModule;
import org.apache.log4j.Logger;

public class QauthModule extends UserModule
{

    public QauthModule(UserModuleInterface usermoduleinterface)
    {
        super(usermoduleinterface);
        setDefault("limit,reauth", "admins,opped");
        if("quakenet".equals(usermoduleinterface.getNetwork()))
            register(com.echbot.messages.in.ChatMessageIn.class);
    }

    public void initialise(Object obj)
    {
        if("quakenet".equals(parent.getNetwork()))
            authWithQ();
    }

    public void received(ChatMessageIn chatmessagein)
    {
        AuthModule authmodule;
        try
        {
            authmodule = (AuthModule)getModule("auth");
        }
        catch(com.echbot.UserModule.NoSuchModuleException nosuchmoduleexception)
        {
            log.warn("Requires auth module", nosuchmoduleexception);
            return;
        }
        if(chatmessagein.isCommand() && "!reauth".equals(chatmessagein.getCommand()) && authmodule.permittedTo(chatmessagein.getFromWithHost(), "limit,reauth", chatmessagein.getTo()))
            authWithQ();
    }

    private void authWithQ()
    {
        String s = parent.getName();
        if("bot1".equals(s))
            auth("ra3bot", "7ropYU1KEv");
        else
        if("bot2".equals(s))
            auth("echbot2", "????????");
        else
        if("bot3".equals(s))
            auth("echbot3", "????????");
        else
        if("bot4".equals(s))
            auth("echbot4", "????????");
        else
        if("bot5".equals(s))
            auth("echbot5", "????????");
        else
        if("bot6".equals(s))
            auth("echbot6", "????????");
        else
        if("bot7".equals(s))
            auth("echbot7", "????????");
        else
        if("bot8".equals(s))
            auth("echbot8", "????????");
        else
        if("bot9".equals(s))
            auth("echbot9", "????????");
        else
        if("bot10".equals(s))
            auth("echbot10", "????????");
        else
        if("bot11".equals(s))
            auth("echbot11", "????????");
        else
        if("bot12".equals(s))
            auth("echbot12", "????????");
        else
        if("bot13".equals(s))
            auth("echbot13", "????????");
        else
        if("bot14".equals(s))
            auth("echbot14", "????????");
        else
        if("bot15".equals(s))
            auth("echbot15", "????????");
    }

    private void auth(String s, String s1)
    {
        send(new ChatMessageOut("Q@Cserve.quakenet.org", "auth " + s + " " + s1, true));
    }

    private static final Logger log;

    static 
    {
        log = Logger.getLogger(com.echbot.modules.qauth.QauthModule.class);
    }
}
