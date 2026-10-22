package com.lerchenflo.relisto.sharedUi

/**
 * True on touch devices (Android): no file drag and drop from other apps, and dragging
 * starts with a long press so lists can still scroll. False on desktop (mouse).
 */
expect val usesTouchInput: Boolean
