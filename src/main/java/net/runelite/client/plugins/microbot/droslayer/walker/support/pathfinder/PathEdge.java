package net.runelite.client.plugins.microbot.droslayer.walker.support.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One materialized edge in a chosen local pathfinder route. */
public final class PathEdge
{
	private final WorldPoint from;
	private final WorldPoint to;
	private final Transport transport;

	PathEdge(WorldPoint from, WorldPoint to, Transport transport)
	{
		this.from = from;
		this.to = to;
		this.transport = transport;
	}

	static List<PathEdge> fromForwardChain(Node lastNode)
	{
		if (lastNode == null)
		{
			return Collections.emptyList();
		}
		List<Node> nodes = lastNode.getNodePath();
		List<PathEdge> edges = new ArrayList<>(Math.max(0, nodes.size() - 1));
		for (int i = 1; i < nodes.size(); i++)
		{
			Node from = nodes.get(i - 1);
			Node to = nodes.get(i);
			Transport transport = to instanceof TransportNode
				? ((TransportNode) to).getTransport()
				: null;
			edges.add(new PathEdge(
				WorldPointUtil.unpackWorldPoint(from.packedPosition),
				WorldPointUtil.unpackWorldPoint(to.packedPosition),
				transport));
		}
		return Collections.unmodifiableList(edges);
	}

	/**
	 * Combine a normal start-to-meeting chain with the reverse-search meeting-to-goal chain.
	 * Reverse transport metadata belongs to the {@code from} node, unlike a forward chain where it
	 * belongs to the {@code to} node.
	 */
	static List<PathEdge> fromBidirectionalChains(Node forwardAtMeet, Node backwardAtMeet)
	{
		List<PathEdge> edges = new ArrayList<>(fromForwardChain(forwardAtMeet));
		for (Node from = backwardAtMeet; from != null && from.previous != null; from = from.previous)
		{
			Node to = from.previous;
			Transport transport = from instanceof TransportNode
				? ((TransportNode) from).getTransport()
				: null;
			edges.add(new PathEdge(
				WorldPointUtil.unpackWorldPoint(from.packedPosition),
				WorldPointUtil.unpackWorldPoint(to.packedPosition),
				transport));
		}
		return Collections.unmodifiableList(edges);
	}

	public WorldPoint getFrom()
	{
		return from;
	}

	public WorldPoint getTo()
	{
		return to;
	}

	public Transport getTransport()
	{
		return transport;
	}

	public boolean isTransport()
	{
		return transport != null;
	}
}
