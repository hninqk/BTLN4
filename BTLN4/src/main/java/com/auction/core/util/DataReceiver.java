package com.auction.core.util;
import com.auction.ui.util.*;
import com.auction.core.util.*;
import com.auction.api.config.*;
import com.auction.infra.db.*;

/**
 * Interface for controllers that accept data from navigation.
 */
public interface DataReceiver {
    void receiveData(Object data);
}
