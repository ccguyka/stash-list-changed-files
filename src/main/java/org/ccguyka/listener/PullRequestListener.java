package org.ccguyka.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.atlassian.event.api.EventListener;
import com.atlassian.stash.commit.Changeset;
import com.atlassian.stash.commit.ChangesetsRequest;
import com.atlassian.stash.commit.Commit;
import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.content.Change;
import com.atlassian.stash.content.ChangeType;
import com.atlassian.stash.event.pull.PullRequestOpenedEvent;
import com.atlassian.stash.event.pull.PullRequestReopenedEvent;
import com.atlassian.stash.event.pull.PullRequestRescopedEvent;
import com.atlassian.stash.event.pull.PullRequestUpdatedEvent;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestService;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageProvider;
import com.atlassian.stash.util.PageRequest;
import com.atlassian.stash.util.PageRequestImpl;
import com.atlassian.stash.util.PagedIterable;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class PullRequestListener {

	private static final PageRequestImpl PAGE_REQUEST = new PageRequestImpl(0, 100);
	private static final int MAX_CHANGES = 100;

	private PullRequestService pullRequestService;
	private CommitService commitService;

	public PullRequestListener(PullRequestService pullRequestService, CommitService commitService) {
		this.pullRequestService = pullRequestService;
		this.commitService = commitService;
	}

	@EventListener
    public void created(PullRequestOpenedEvent event) {
		update(event.getPullRequest());
    }

    @EventListener
    public void reopened(PullRequestReopenedEvent event) {
    	update(event.getPullRequest());
    }

    @EventListener
    public void rescoped(PullRequestRescopedEvent event) {
    	update(event.getPullRequest());
    }

    @EventListener
    public void updated(PullRequestUpdatedEvent event) {
        update(event.getPullRequest());
    }

	private void update(PullRequest pullRequest) {

		updateUsingCommits(pullRequest);
	}

	private void updateUsingCommits(PullRequest pullRequest) {

		System.out.println("changes in pr: " + pullRequest.getId());
		List<String> commitIds = getCommitIds(pullRequest);
		System.out.println("Commits:");
		for (String commitId : commitIds) {
			System.out.println(commitId);
		}

		Iterable<Change> changes = getChanges(pullRequest, commitIds);
		for (Change change : changes) {
			String name = change.getPath().getName();
			ChangeType changeType = change.getType();
			System.out.println("File: " + name + ", mod: " + changeType);
		}

		//add srcPath = null;     fromContentId = 000000....0000;
		//mod srcPath = null;     fromContentId = hash;
		//mov srcPath = old path; fromContentId = hash;
	}

	private Iterable<Change> getChanges(PullRequest pullRequest,
			List<String> commitIds) {
		final Repository repository = pullRequest.getFromRef().getRepository();
		Iterable<Changeset> changesets = getChangesets(repository, commitIds);
		Iterable<Change> changes = Iterables.concat(Iterables.transform(
				changesets, CHANGES));
		return changes;
	}

	private List<String> getCommitIds(PullRequest pullRequest) {
		Iterable<Commit> commits = getCommits(pullRequest);
		List<String> commitIds = new ArrayList<String>();
		Iterables.addAll(commitIds, Iterables.transform(commits, COMMIT_ID));

		return Lists.reverse(commitIds);
	}

	private Iterable<Commit> getCommits(final PullRequest pullRequest) {
		final Repository repository = pullRequest.getFromRef().getRepository();
		return new PagedIterable<Commit>(new PageProvider<Commit>() {
			@Override
			public Page<Commit> get(PageRequest pageRequest) {
				return pullRequestService.getCommits(repository.getId(),
						pullRequest.getId(), pageRequest);
			}
		}, PAGE_REQUEST);
	}

	private Iterable<Changeset> getChangesets(final Repository repository, final Iterable<String> commitIds) {
		return new PagedIterable<Changeset>(new PageProvider<Changeset>() {
			@Override
			public Page<Changeset> get(PageRequest pageRequest) {
				return commitService.getChangesets(new ChangesetsRequest.Builder(repository)
						.commitIds(commitIds)
						.maxChangesPerCommit(MAX_CHANGES)
						.build(), pageRequest);
			}
		}, PAGE_REQUEST);
	}

	private static final Function<Commit, String> COMMIT_ID = new Function<Commit, String>() {
		@Override
		public String apply(Commit commit) {
			return commit.getId();
		}
	};

	private static final Function<Changeset, Iterable<Change>> CHANGES = new Function<Changeset, Iterable<Change>>() {
        @Override
		public Iterable<Change> apply(Changeset input) {
			return (Iterable<Change>) input.getChanges().getValues();
		}
	};
}
