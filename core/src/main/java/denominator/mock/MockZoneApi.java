package denominator.mock;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListSet;

import denominator.model.ResourceRecordSet;
import denominator.model.Zone;
import denominator.model.rdata.SOAData;

import static denominator.common.Preconditions.checkArgument;
import static denominator.common.Preconditions.checkState;
import static denominator.common.Util.filter;
import static denominator.model.ResourceRecordSets.nameAndTypeEqualTo;
import static denominator.model.ResourceRecordSets.ns;
import static denominator.model.Zones.nameEqualTo;

final class MockZoneApi implements denominator.ZoneApi {

  private static final Comparator<ResourceRecordSet<?>> TO_STRING =
      new Comparator<ResourceRecordSet<?>>() {
        @Override
        public int compare(ResourceRecordSet<?> arg0, ResourceRecordSet<?> arg1) {
          return arg0.toString().compareTo(arg1.toString());
        }
      };

  private final Map<String, Collection<ResourceRecordSet<?>>> data;

  MockZoneApi(Map<String, Collection<ResourceRecordSet<?>>> data) {
    this.data = data;
    create("denominator.io.");
  }

  public void create(String name) {
    checkArgument(!data.containsKey(name), "zone %s already exists", name);
    Collection<ResourceRecordSet<?>> zone =
        new ConcurrentSkipListSet<ResourceRecordSet<?>>(TO_STRING);
    zone.add(ResourceRecordSet.builder()
                 .type("SOA")
                 .name(name)
                 .ttl(3600)
                 .add(SOAData.builder().mname("ns1." + name).rname("admin." + name)
                          .serial(1).refresh(3600).retry(600).expire(604800).minimum(86400).build())
                 .build());
    zone.add(ns(name, 86400, "ns1." + name));
    data.put(name, zone);
  }

  @Override
  public Iterator<Zone> iterator() {
    final Iterator<Entry<String, Collection<ResourceRecordSet<?>>>>
        delegate = data.entrySet().iterator();
    return new Iterator<Zone>() {
      @Override
      public boolean hasNext() {
        return delegate.hasNext();
      }

      @Override
      public Zone next() {
        Entry<String, Collection<ResourceRecordSet<?>>> next = delegate.next();
        String name = next.getKey();
        Iterator<ResourceRecordSet<?>> soa =
            filter(next.getValue().iterator(), nameAndTypeEqualTo(name, "SOA"));

        checkState(soa.hasNext(), "SOA record for zone %s was not present", name);

        SOAData soaData = (SOAData) soa.next().records().get(0);
        return Zone.builder()
            .name(name)
            .id(name)
            .ttl(soaData.minimum())
            .email(soaData.rname()).build();
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException("remove");
      }
    };
  }

  @Override
  public Iterator<Zone> iterateByName(String name) {
    return filter(iterator(), nameEqualTo(name));
  }
}
