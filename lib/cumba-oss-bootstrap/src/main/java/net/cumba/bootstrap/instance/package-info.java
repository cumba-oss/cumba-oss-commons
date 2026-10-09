/**
 * The launcher's half of the single-instance hand-off: the per-user instance lock, the client that
 * delivers a start's files to the running primary, and the seams that let every branch run on a
 * Linux build host. Entered only by a distribution whose config names
 * {@code [bootstrap] single-instance}; see {@link net.cumba.bootstrap.instance.InstanceGate}.
 */
@org.jspecify.annotations.NullMarked
package net.cumba.bootstrap.instance;
