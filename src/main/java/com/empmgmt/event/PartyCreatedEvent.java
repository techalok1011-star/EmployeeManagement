package com.empmgmt.event;

import com.empmgmt.entity.Party;
import lombok.Getter;

/**
 * Published whenever a party is created through the app (admin/accountant Add Party, or the
 * quick-add page managers and employees are sent to from an entry form). Listeners decide what to
 * do with it - see {@link com.empmgmt.service.AdminNotificationListener}.
 */
@Getter
public class PartyCreatedEvent {

    private final Party party;
    private final String actorUsername;

    public PartyCreatedEvent(Party party, String actorUsername) {
        this.party = party;
        this.actorUsername = actorUsername;
    }
}
